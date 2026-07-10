package com.meada.profiles.concessionaria;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.concessionaria.config.ConcessionariaConfig;
import com.meada.profiles.concessionaria.config.ConcessionariaConfigRepository;
import com.meada.profiles.concessionaria.salespeople.ConcessionariaSalesperson;
import com.meada.profiles.concessionaria.salespeople.ConcessionariaSalespersonRepository;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDrive;
import com.meada.profiles.concessionaria.testdrives.ConcessionariaTestDriveRepository;
import com.meada.profiles.concessionaria.vehicles.ConcessionariaVehicle;
import com.meada.profiles.concessionaria.vehicles.ConcessionariaVehicleRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do ConcessionariaBot (camada 8.17).
 *
 * <p>A persona base do concessionaria vem da SM-A (texto fixo em
 * {@link com.meada.profiles.ProfilePromptContext}). Esta classe acrescenta o contexto
 * DINÂMICO: a VITRINE (veículos status='disponivel'+active) + vendedores ativos + slots livres POR
 * VENDEDOR (próximos 14 dias) + nome da loja + instruções das DUAS tags (&lt;testdrive_carro&gt; +
 * &lt;lead_carro&gt;).
 *
 * <p>TTL 30s. Keyed por {@code (companyId, contactId)}. Os services de veículo/vendedor/test-drive/
 * lead/config chamam {@link #invalidate} (por company) ao mutar. Veículos reservado/vendido NÃO
 * entram na vitrine.
 */
@Component
public class ConcessionariaContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 14;
    private static final int MAX_SLOTS_PER_DAY = 6;        // limita a lista no prompt (resumida).
    private static final int MAX_SALESPEOPLE_WITH_SLOTS = 5; // não estourar o prompt com N vendedores.

    private final ConcessionariaVehicleRepository vehicleRepository;
    private final ConcessionariaSalespersonRepository salespersonRepository;
    private final ConcessionariaTestDriveRepository testDriveRepository;
    private final ConcessionariaConfigRepository configRepository;
    private final Cache<String, String> cache;

    public ConcessionariaContextCache(ConcessionariaVehicleRepository vehicleRepository,
                                      ConcessionariaSalespersonRepository salespersonRepository,
                                      ConcessionariaTestDriveRepository testDriveRepository,
                                      ConcessionariaConfigRepository configRepository) {
        this.vehicleRepository = vehicleRepository;
        this.salespersonRepository = salespersonRepository;
        this.testDriveRepository = testDriveRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(1000)
            .build();
    }

    /** Bloco de contexto dinâmico p/ a conversa. null-safe (contactId pode ser null). */
    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de veículo/vendedor/test-drive/lead/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        ConcessionariaConfig config = configRepository.findByCompany(companyId);
        StringBuilder sb = new StringBuilder();

        if (config.businessName() != null && !config.businessName().isBlank()) {
            sb.append("LOJA: ").append(config.businessName()).append("\n\n");
        }

        // --- VITRINE (veículos disponíveis e ativos) ---
        List<ConcessionariaVehicle> vitrine = vehicleRepository.listAvailable(companyId);
        sb.append("ESTOQUE DISPONÍVEL (vitrine):\n");
        if (vitrine.isEmpty()) {
            sb.append("(nenhum veículo disponível no momento — informe o cliente e ofereça anotar o "
                + "contato p/ avisar quando chegar.)\n");
        } else {
            for (ConcessionariaVehicle v : vitrine) {
                sb.append("- id ").append(v.id()).append(": ").append(v.brand()).append(" ").append(v.model());
                if (v.modelYear() != null) {
                    sb.append(" ").append(v.modelYear());
                }
                sb.append(" — ").append(brl(v.priceCents()));
                if (v.mileageKm() != null) {
                    sb.append(", ").append(v.mileageKm()).append(" km");
                }
                if (v.color() != null && !v.color().isBlank()) {
                    sb.append(", ").append(v.color());
                }
                if (v.fuel() != null && !v.fuel().isBlank()) {
                    sb.append(", ").append(v.fuel());
                }
                if (v.transmission() != null && !v.transmission().isBlank()) {
                    sb.append(", ").append(v.transmission());
                }
                if (v.description() != null && !v.description().isBlank()) {
                    sb.append(". ").append(v.description());
                }
                if (v.photoUrl() != null && !v.photoUrl().isBlank()) {
                    sb.append(" [foto: ").append(v.photoUrl()).append("]");
                }
                sb.append("\n");
            }
        }
        sb.append("\n");

        // --- VENDEDORES ATIVOS + SLOTS LIVRES POR VENDEDOR (test-drive) ---
        List<ConcessionariaSalesperson> salespeople = salespersonRepository.listByCompany(companyId, true);
        sb.append("VENDEDORES (para test-drive):\n");
        if (salespeople.isEmpty()) {
            sb.append("(nenhum vendedor ativo — não há como agendar test-drive agora.)\n");
        } else {
            int shown = 0;
            for (ConcessionariaSalesperson sp : salespeople) {
                sb.append("- id ").append(sp.id()).append(": ").append(sp.name()).append("\n");
                if (shown < MAX_SALESPEOPLE_WITH_SLOTS) {
                    sb.append(buildSlotsSegment(companyId, sp.id(), config));
                    shown++;
                }
            }
        }
        sb.append("\n");

        // --- TEST-DRIVES FUTUROS DO CLIENTE (onda 1 #3 — pra fechar o loop do lembrete) ---
        if (contactId != null) {
            List<ConcessionariaTestDrive> upcoming =
                testDriveRepository.listUpcomingByContact(companyId, contactId, 5);
            if (!upcoming.isEmpty()) {
                sb.append("TEST-DRIVES FUTUROS DESTE CLIENTE:\n");
                for (ConcessionariaTestDrive t : upcoming) {
                    ZonedDateTime z = t.startAt().atZone(TENANT_ZONE);
                    sb.append("- id ").append(t.id()).append(": ").append(t.vehicleBrand()).append(" ")
                        .append(t.vehicleModel()).append(" em ").append(DATE_FMT.format(z))
                        .append(" às ").append(TIME_FMT.format(z))
                        .append(" · status ").append(t.status()).append("\n");
                }
                sb.append("Quando o cliente RESPONDER a um lembrete confirmando (SIM) ou pedindo pra "
                    + "desmarcar, você só REFLETE a decisão dele — termine a mensagem com a tag:\n")
                    .append("<confirmacao_testdrive>{\"test_drive_id\":\"<id>\",\"decisao\":"
                        + "\"confirmado|cancelado\"}</confirmacao_testdrive>\n")
                    .append("Confirmar só vale para test-drive 'agendado'; cancelar vale para agendado/"
                        + "confirmado. NUNCA confirme ou cancele sem o cliente pedir.\n\n");
            }
        }

        // --- INSTRUÇÕES (2 tags) ---
        sb.append("INSTRUÇÕES:\n")
            .append("Você só MOSTRA o estoque disponível, AGENDA test-drive e REGISTRA interesse de "
                + "compra (lead). NUNCA feche preço/desconto/financiamento, NUNCA aprove crédito, NUNCA "
                + "prometa entrega ou condição que não esteja no estoque. test-drive e lead são SÓ de "
                + "veículo da vitrine (disponível).\n")
            .append("Quando o cliente CONFIRMAR um test-drive (escolheu o veículo da vitrine, um "
                + "vendedor, e dia/hora dentro do horário), sua ÚLTIMA mensagem deve TERMINAR com a tag "
                + "(em uma linha própria, sem markdown):\n")
            .append("<testdrive_carro>{\"vehicle_id\":\"<id>\",\"salesperson_id\":\"<id>\","
                + "\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...|null\"}</testdrive_carro>\n")
            .append("Quando o cliente DEMONSTRAR INTERESSE DE COMPRA de um veículo (e a condição de "
                + "pagamento à vista ou financiado), registre o lead — sua ÚLTIMA mensagem deve TERMINAR "
                + "com a tag:\n")
            .append("<lead_carro>{\"vehicle_id\":\"<id>\",\"payment_condition\":\"avista|financiado\","
                + "\"notes\":\"...|null\"}</lead_carro>\n")
            .append("Use sempre o id exato do veículo/vendedor da lista acima. NUNCA invente preço — o "
                + "preço do lead é sempre o do estoque.\n")
            .append("LISTA DE DESEJOS (quando a vitrine NÃO tem o que o cliente procura): ofereça "
                + "registrar o interesse pra loja avisar assim que chegar — colete marca/modelo (pelo "
                + "menos um), opcionalmente o teto de preço e o ano mínimo QUE O CLIENTE DECLAROU, e "
                + "termine a mensagem com a tag:\n")
            .append("TROCA (trade-in): se o cliente quiser dar o carro ATUAL na troca, COLETE marca, "
                + "modelo, ano, km, estado geral e (se ele declarar) o valor pretendido — e termine com "
                + "(linha própria, sem markdown): <troca_carro>{\"brand\":\"...\",\"model\":\"...\","
                + "\"year\":N,\"km\":N,\"condition\":\"...\",\"asking_cents\":N,"
                + "\"interest_vehicle_id\":\"UUID|null\"}</troca_carro> — NUNCA avalie nem prometa "
                + "valor pelo usado (a avaliação é da equipe; diga que a loja retorna com a proposta).\n")
            .append("<desejo_carro>{\"brand\":\"...|null\",\"model\":\"...|null\","
                + "\"max_price_cents\":N|null,\"min_year\":N|null,\"notes\":\"...|null\"}</desejo_carro>\n")
            .append("NUNCA prometa quando o carro chega nem reserve por conta própria — o aviso é "
                + "automático quando um veículo compatível entrar no estoque.\n\n");

        return sb.toString();
    }

    /**
     * Lista resumida de horários livres de UM VENDEDOR nos próximos {@value #CONTEXT_DAYS} dias. Para
     * cada dia, gera os slots de {@code duration_minutes} entre opens_at e closes_at e remove os
     * ocupados por test-drives ativos (agendado/confirmado) DAQUELE vendedor. Limita a
     * {@value #MAX_SLOTS_PER_DAY} por dia.
     */
    private String buildSlotsSegment(UUID companyId, UUID salespersonId, ConcessionariaConfig config) {
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        List<ConcessionariaTestDrive> active =
            testDriveRepository.listActiveBySalesperson(companyId, salespersonId, now, until);

        StringBuilder sb = new StringBuilder("  horários livres (próximos ")
            .append(CONTEXT_DAYS).append(" dias, resumido):\n");
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);
        boolean anyDay = false;
        for (int d = 0; d < CONTEXT_DAYS; d++) {
            LocalDate day = startDay.plusDays(d).toLocalDate();
            List<String> free = new ArrayList<>();
            LocalTime t = config.opensAt();
            while (!t.plusMinutes(config.durationMinutes()).isAfter(config.closesAt())
                    && free.size() < MAX_SLOTS_PER_DAY) {
                ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                ZonedDateTime slotEnd = slotStart.plusMinutes(config.durationMinutes());
                boolean inFuture = slotStart.toInstant().isAfter(now);
                boolean occupied = active.stream().anyMatch(a ->
                    !(a.endAt().compareTo(slotStart.toInstant()) <= 0
                        || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                if (inFuture && !occupied) {
                    free.add(TIME_FMT.format(t));
                }
                t = t.plusMinutes(config.durationMinutes());
            }
            if (!free.isEmpty()) {
                anyDay = true;
                sb.append("    ").append(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)))
                    .append(": ").append(String.join(", ", free)).append("\n");
            }
        }
        if (!anyDay) {
            sb.append("    (sem horário livre nos próximos dias)\n");
        }
        return sb.toString();
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
