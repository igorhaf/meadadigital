package com.meada.profiles.otica;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.otica.appointments.OticaExamAppointment;
import com.meada.profiles.otica.appointments.OticaExamRepository;
import com.meada.profiles.otica.catalog.OticaCatalogItem;
import com.meada.profiles.otica.catalog.OticaCatalogItemRepository;
import com.meada.profiles.otica.catalog.OticaCatalogOption;
import com.meada.profiles.otica.config.OticaConfig;
import com.meada.profiles.otica.config.OticaConfigRepository;
import com.meada.profiles.otica.professionals.OticaProfessional;
import com.meada.profiles.otica.professionals.OticaProfessionalRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do OticaBot (camada 8.12, PRIMEIRO HÍBRIDO).
 * Injeta os DOIS fluxos num único bloco:
 * <ul>
 *   <li>FLUXO A (exame): optometristas ativos + slots livres por profissional (próximos 14 dias);</li>
 *   <li>FLUXO B (encomenda): catálogo (armações/lentes/acessórios — marcando SOB ENCOMENDA com o lead)
 *       + as opções de tipo de lente/tratamento com os deltas + mínimo + lead default;</li>
 *   <li>as DUAS tags ({@code <exame_otica>} e {@code <encomenda_otica>}) + a trava de comportamento.</li>
 * </ul>
 *
 * <p>TTL 30s (igual ao chassi clínico dental). Keyed por {@code (companyId, contactId)}. Os services
 * de profissional/exame/catálogo/config chamam {@link #invalidate} (por company) ao mutar — a IA vê a
 * mudança na hora. Clone combinado do DentalContextCache (slots) + FloriculturaCatalogCache (catálogo).
 */
@Component
public class OticaContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 14;
    private static final int MAX_SLOTS_PER_PROF_DAY = 6;

    private final OticaProfessionalRepository professionalRepository;
    private final OticaExamRepository examRepository;
    private final OticaCatalogItemRepository catalogRepository;
    private final OticaConfigRepository configRepository;
    private final Cache<String, String> cache;

    public OticaContextCache(OticaProfessionalRepository professionalRepository,
                             OticaExamRepository examRepository,
                             OticaCatalogItemRepository catalogRepository,
                             OticaConfigRepository configRepository) {
        this.professionalRepository = professionalRepository;
        this.examRepository = examRepository;
        this.catalogRepository = catalogRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(1000)
            .build();
    }

    /** Bloco de contexto dinâmico p/ a conversa. null-safe. */
    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de profissional/exame/catálogo/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        OticaConfig config = configRepository.findByCompany(companyId);
        List<OticaProfessional> pros = professionalRepository.listByCompany(companyId, true);
        StringBuilder sb = new StringBuilder();

        // ===== FLUXO A — EXAME =====
        if (pros.isEmpty()) {
            sb.append("OPTOMETRISTAS: (nenhum ativo no momento.)\n\n");
        } else {
            sb.append("OPTOMETRISTAS (use o professional_id EXATO p/ marcar exame):\n");
            for (OticaProfessional p : pros) {
                sb.append("- ").append(p.id()).append(" · ").append(p.name()).append("\n");
            }
            sb.append("\n");
        }

        // --- EXAMES FUTUROS DO CLIENTE (onda 1, backlog #1 — a IA captura confirmação/cancelamento) ---
        if (contactId != null) {
            java.util.List<com.meada.profiles.otica.appointments.OticaExamAppointment> upcoming =
                examRepository.listByContact(companyId, contactId, true);
            if (!upcoming.isEmpty()) {
                sb.append("EXAMES FUTUROS DESTE CLIENTE (use o id EXATO na tag de confirmação):\n");
                for (var e : upcoming) {
                    java.time.ZonedDateTime z = e.startAt().atZone(java.time.ZoneId.of("America/Sao_Paulo"));
                    sb.append("- ").append(e.id()).append(" · ")
                        .append(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm").format(z))
                        .append(" com ").append(e.professionalName())
                        .append(" · status ").append(e.status()).append("\n");
                }
                sb.append("Quando o cliente CONFIRMAR um exame futuro (ex.: responder SIM ao lembrete) ou "
                    + "pedir para DESMARCAR, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria):\n"
                    + "<confirmacao_exame>{\"exam_id\":\"UUID\",\"decisao\":\"confirmado|cancelado\"}"
                    + "</confirmacao_exame>\n"
                    + "Você só REFLETE a decisão do cliente — NUNCA confirme ou cancele sem ele pedir.\n\n");
            }
        }
        sb.append(buildSlotsSegment(companyId, config, pros));

        // ===== FLUXO B — ENCOMENDA =====
        sb.append(buildCatalogSegment(companyId, config));

        // ===== INSTRUÇÕES + TRAVA + TAGS =====
        sb.append("INSTRUÇÕES E LIMITES (LEIA COM ATENÇÃO):\n")
            .append("Você faz DUAS coisas na ótica: (1) AGENDA EXAME DE VISTA com um optometrista e (2) "
                + "monta a ENCOMENDA de óculos sob receita. NUNCA dê diagnóstico, NUNCA receite grau, "
                + "NUNCA interprete/calcule a receita do cliente — apenas REGISTRE o grau que ele "
                + "fornecer. Se o cliente não tem a receita, registre como pendente (ele traz depois). "
                + "NUNCA invente preço, item, opção ou prazo fora do catálogo acima.\n")
            .append("Para MARCAR um exame, termine com a tag (linha própria, sem markdown):\n")
            .append("<exame_otica>{\"professional_id\":\"UUID\",\"date\":\"YYYY-MM-DD\","
                + "\"start_time\":\"HH:MM\",\"notes\":\"...|null\"}</exame_otica>\n")
            .append("Para registrar a ENCOMENDA de óculos (quando o cliente CONFIRMAR itens), termine com:\n")
            .append("<encomenda_otica>{\"items\":[{\"catalog_item_id\":\"UUID\","
                + "\"options\":[{\"option_id\":\"UUID\"}],\"quantity\":N}],\"ready_date\":\"YYYY-MM-DD|null\","
                + "\"rx\":{\"od\":{\"spherical\":\"-1.00\",\"cylindrical\":\"-0.50\",\"axis\":90},"
                + "\"oe\":{\"spherical\":\"-1.25\",\"cylindrical\":\"-0.75\",\"axis\":85},\"pd\":\"62.0\"}|null,"
                + "\"prescription_pending\":true|false,\"notes\":\"...\"}</encomenda_otica>\n")
            .append("Item SOB ENCOMENDA exige ready_date dentro do prazo de montagem (hoje + lead). Item "
                + "que não é sob encomenda (acessório) pode ir sem ready_date. Se o cliente vai trazer a "
                + "receita, use prescription_pending=true e omita o grau. Use ids EXATOS do catálogo. "
                + "Óculos pronto = RETIRADA na loja. Avise que a encomenda vai para confirmação da loja.\n\n");

        return sb.toString();
    }

    /**
     * Lista resumida de horários livres por profissional nos próximos {@value #CONTEXT_DAYS} dias.
     * Para cada profissional/dia, gera os slots de {@code exam_duration_minutes} entre opens_at e
     * closes_at e remove os ocupados por exames ativos (agendado/confirmado) DAQUELE profissional.
     */
    private String buildSlotsSegment(UUID companyId, OticaConfig config, List<OticaProfessional> pros) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES PRA EXAME (próximos ")
            .append(CONTEXT_DAYS).append(" dias, por optometrista):\n");
        if (pros.isEmpty()) {
            sb.append("(sem optometristas ativos.)\n\n");
            return sb.toString();
        }
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);
        int duration = config.examDurationMinutes();

        for (OticaProfessional p : pros) {
            List<OticaExamAppointment> active =
                examRepository.listActiveByProfessional(companyId, p.id(), now, until);
            List<String> dayChunks = new ArrayList<>();
            for (int d = 0; d < CONTEXT_DAYS; d++) {
                LocalDate day = startDay.plusDays(d).toLocalDate();
                List<String> free = new ArrayList<>();
                LocalTime t = config.opensAt();
                while (!t.plusMinutes(duration).isAfter(config.closesAt())
                        && free.size() < MAX_SLOTS_PER_PROF_DAY) {
                    ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                    ZonedDateTime slotEnd = slotStart.plusMinutes(duration);
                    boolean inFuture = slotStart.toInstant().isAfter(now);
                    boolean occupied = active.stream().anyMatch(a ->
                        !(a.endAt().compareTo(slotStart.toInstant()) <= 0
                            || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                    if (inFuture && !occupied) {
                        free.add(TIME_FMT.format(t));
                    }
                    t = t.plusMinutes(duration);
                }
                if (!free.isEmpty()) {
                    dayChunks.add(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)) + " " + String.join(", ", free));
                }
            }
            if (!dayChunks.isEmpty()) {
                sb.append("- ").append(p.name()).append(": ").append(String.join(" | ", dayChunks)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Bloco do catálogo (FLUXO B) com os item_id/option_id EXATOS, marcando SOB ENCOMENDA (com o lead)
     * e os deltas das opções (tipo de lente/tratamento). + mínimo + lead default. Espelho do
     * FloriculturaCatalogCache.
     */
    private String buildCatalogSegment(UUID companyId, OticaConfig config) {
        List<OticaCatalogItem> items = catalogRepository.listByCompany(companyId, null, true);
        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CATÁLOGO DE ÓCULOS: (nenhum item disponível no momento.)\n\n");
        } else {
            sb.append("CATÁLOGO DE ÓCULOS (use o catalog_item_id EXATO):\n");
            String currentCategory = null;
            for (OticaCatalogItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(OticaCategory.fromId(currentCategory)
                        .map(OticaCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents()));
                if (it.madeToOrder()) {
                    int lead = it.leadTimeDays() == null ? config.leadTimeDaysDefault() : it.leadTimeDays();
                    sb.append(" · SOB ENCOMENDA (prazo de montagem ").append(lead).append(" dia(s))");
                }
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                sb.append("\n");
                appendOptions(sb, it.options());
            }
            sb.append("\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise se ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        sb.append("Prazo de montagem padrão: ").append(config.leadTimeDaysDefault()).append(" dia(s).\n\n");
        return sb.toString();
    }

    /** Lista as opções available=true de um item, agrupadas por group_label: {@code [grupo] opt_id label (+R$ delta) | ...}. */
    private void appendOptions(StringBuilder sb, List<OticaCatalogOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (OticaCatalogOption opt : options) {
            if (!opt.available()) {
                continue;
            }
            StringBuilder line = byGroup.computeIfAbsent(opt.groupLabel(), g -> new StringBuilder());
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(opt.id()).append(" ").append(opt.optionLabel())
                .append(" (+R$ ").append(formatBrl(opt.priceDeltaCents())).append(")");
        }
        for (Map.Entry<String, StringBuilder> e : byGroup.entrySet()) {
            sb.append("    [").append(e.getKey()).append("] ").append(e.getValue()).append("\n");
        }
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
