package com.meada.profiles.barbearia;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.barbearia.appointments.BarberAppointment;
import com.meada.profiles.barbearia.appointments.BarberAppointmentRepository;
import com.meada.profiles.barbearia.barbers.BarberBarber;
import com.meada.profiles.barbearia.barbers.BarberBarberRepository;
import com.meada.profiles.barbearia.config.BarberConfig;
import com.meada.profiles.barbearia.config.BarberConfigRepository;
import com.meada.profiles.barbearia.loyalty.BarberLoyaltyConfig;
import com.meada.profiles.barbearia.loyalty.BarberLoyaltyConfigRepository;
import com.meada.profiles.barbearia.queue.BarberQueueRepository;
import com.meada.profiles.barbearia.queue.BarberQueueTicket;
import com.meada.profiles.barbearia.services.BarberService;
import com.meada.profiles.barbearia.services.BarberServiceRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de contexto dinâmico injetado no prompt do BarbeariaBot (camada 8.1).
 *
 * <p>TTL 10s — A MAIS CURTA do projeto (abaixo dos 15s do restaurant), porque a FILA muda a cada
 * cliente. Keyed por {@code (companyId, contactId)}. Todos os services (barbeiro/serviço/agendamento/
 * fila/config) chamam {@link #invalidate} (por company).
 *
 * <p>Conteúdo: serviços ativos (com duração/preço) + barbeiros ativos + TAMANHO ATUAL DA FILA por
 * barbeiro (e fila geral) + agenda/slots livres por barbeiro (próximos 3 dias) + histórico do contato
 * (se identificado) + instruções das DUAS tags (<agendamento_barbearia> e <fila_barbearia>). Fuso
 * America/Sao_Paulo (hardcoded, pendência).
 */
@Component
public class BarberContextCache {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int CONTEXT_DAYS = 3;            // próximos 3 dias (fila é "agora")
    private static final int MAX_SLOTS_PER_BARBER_DAY = 6;
    private static final int HISTORY_LIMIT = 5;

    private final BarberBarberRepository barberRepository;
    private final BarberServiceRepository serviceRepository;
    private final BarberAppointmentRepository appointmentRepository;
    private final BarberQueueRepository queueRepository;
    private final BarberConfigRepository configRepository;
    private final BarberLoyaltyConfigRepository loyaltyRepository;
    private final Cache<String, String> cache;

    public BarberContextCache(BarberBarberRepository barberRepository,
                              BarberServiceRepository serviceRepository,
                              BarberAppointmentRepository appointmentRepository,
                              BarberQueueRepository queueRepository,
                              BarberConfigRepository configRepository,
                              BarberLoyaltyConfigRepository loyaltyRepository) {
        this.barberRepository = barberRepository;
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
        this.queueRepository = queueRepository;
        this.configRepository = configRepository;
        this.loyaltyRepository = loyaltyRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(10))
            .maximumSize(1000)
            .build();
    }

    public String contextSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Invalida todas as entradas de uma empresa (mutação de barbeiro/serviço/agendamento/fila/config). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        BarberConfig config = configRepository.findByCompany(companyId);
        List<BarberService> services = serviceRepository.listByCompany(companyId, true);
        List<BarberBarber> barbers = barberRepository.listByCompany(companyId, true);

        StringBuilder sb = new StringBuilder();

        // --- SERVIÇOS ---
        if (services.isEmpty()) {
            sb.append("SERVIÇOS DA BARBEARIA: (nenhum serviço ativo no momento.)\n\n");
        } else {
            sb.append("SERVIÇOS DA BARBEARIA (use o service_id EXATO na tag):\n");
            for (BarberService o : services) {
                sb.append("- ").append(o.id()).append(" · ").append(o.name())
                    .append(": ").append(o.durationMinutes()).append("min");
                if (o.priceCents() != null) {
                    sb.append(" (R$ ").append(formatBrl(o.priceCents())).append(")");
                }
                if (o.category() != null && !o.category().isBlank()) {
                    sb.append(" [").append(o.category()).append("]");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // --- BARBEIROS + TAMANHO DA FILA POR BARBEIRO ---
        Map<UUID, Integer> waitingByBarber = waitingCounts(companyId);
        int generalWaiting = waitingByBarber.getOrDefault(null, 0);
        if (barbers.isEmpty()) {
            sb.append("BARBEIROS: (nenhum barbeiro ativo.)\n\n");
        } else {
            sb.append("BARBEIROS (use o barber_id EXATO na tag):\n");
            for (BarberBarber b : barbers) {
                sb.append("- ").append(b.id()).append(" · ").append(b.name());
                if (b.specialty() != null && !b.specialty().isBlank()) {
                    sb.append(" (").append(b.specialty()).append(")");
                }
                int fila = waitingByBarber.getOrDefault(b.id(), 0);
                sb.append(" — fila: ").append(fila).append(" aguardando\n");
            }
            sb.append("FILA GERAL (qualquer barbeiro): ").append(generalWaiting).append(" aguardando.\n\n");
        }

        // --- HISTÓRICO DO CLIENTE (se identificado) ---
        if (contactId != null) {
            List<BarberAppointment> history = appointmentRepository.listByContact(companyId, contactId, HISTORY_LIMIT);
            if (!history.isEmpty()) {
                sb.append("HISTÓRICO DO CLIENTE:\n");
                for (BarberAppointment a : history) {
                    ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(DATE_FMT.format(z)).append(": ").append(a.serviceName())
                        .append(" com ").append(a.barberName()).append("\n");
                }
                sb.append("\n");
            } else {
                sb.append("HISTÓRICO DO CLIENTE: primeira vez (sem agendamentos anteriores).\n\n");
            }

            // --- AGENDAMENTOS FUTUROS (onda 1, backlog #1 — a IA captura confirmação/cancelamento) ---
            List<BarberAppointment> upcoming = appointmentRepository.listUpcomingByContact(companyId, contactId, 5);
            if (!upcoming.isEmpty()) {
                sb.append("AGENDAMENTOS FUTUROS DO CLIENTE (use o id EXATO na tag de confirmação):\n");
                for (BarberAppointment a : upcoming) {
                    ZonedDateTime z = a.startAt().atZone(TENANT_ZONE);
                    sb.append("- ").append(a.id()).append(" · ").append(a.serviceName())
                        .append(" ").append(DATE_FMT.format(z)).append(" às ").append(TIME_FMT.format(z))
                        .append(" com ").append(a.barberName())
                        .append(" · status ").append(a.status()).append("\n");
                }
                sb.append("Quando o cliente CONFIRMAR um horário futuro (ex.: responder SIM ao lembrete) ou "
                    + "pedir para DESMARCAR, sua ÚLTIMA mensagem deve TERMINAR com a tag (linha própria):\n"
                    + "<confirmacao_barbearia>{\"appointment_id\":\"UUID\",\"decisao\":\"confirmado|cancelado\"}"
                    + "</confirmacao_barbearia>\n"
                    + "Você só REFLETE a decisão do cliente — NUNCA confirme ou cancele sem ele pedir.\n\n");
            }

            // --- FIDELIDADE (onda 1, backlog #3) — a IA só INFORMA; quem aplica é o sistema ---
            BarberLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
            if (loyalty.enabled()) {
                int realized = appointmentRepository.countRealizedByContact(companyId, contactId);
                int threshold = loyalty.thresholdCuts();
                sb.append("FIDELIDADE (a cada ").append(threshold).append(" cortes realizados, o próximo é GRÁTIS — "
                    + "aplicado AUTOMATICAMENTE pelo sistema no agendamento): o cliente tem ")
                    .append(realized).append(" corte(s) realizado(s). ");
                if (realized > 0 && realized % threshold == 0) {
                    sb.append("O PRÓXIMO agendamento dele sai grátis — pode avisar!");
                } else {
                    sb.append("Faltam ").append(threshold - (realized % threshold))
                        .append(" para o próximo grátis.");
                }
                sb.append(" Você INFORMA o saldo; NUNCA aplica/promete desconto por conta própria.\n\n");
            }
        } else {
            sb.append("CLIENTE NÃO IDENTIFICADO pelo telefone. Peça o nome para registrar o "
                + "agendamento ou a entrada na fila.\n\n");
        }

        // --- SLOTS LIVRES POR BARBEIRO ---
        sb.append(buildSlotsSegment(companyId, config, barbers, services));

        // --- INSTRUÇÕES + 2 TAGS ---
        sb.append("INSTRUÇÕES (dois caminhos — MARCAR HORÁRIO ou ENTRAR NA FILA):\n")
            .append("O cliente pode (1) MARCAR um horário com um barbeiro, ou (2) ENTRAR NA FILA de "
                + "walk-in (por ordem de chegada, sem hora marcada) quando quer ser atendido 'assim que "
                + "der'. Pergunte qual ele prefere. NUNCA recomende serviço que o cliente não pediu, "
                + "NUNCA opine sobre a aparência/estilo do cliente, sem promessa de resultado de corte.\n");
        if (config.queueEnabled()) {
            sb.append("A fila de walk-in está LIGADA.\n");
        } else {
            sb.append("A fila de walk-in está DESLIGADA — só ofereça MARCAR horário.\n");
        }
        sb.append("Sobre a FILA: a posição e o tempo de espera são SEMPRE ESTIMATIVA ('aproximadamente'), "
                + "porque desistências e horários marcados mexem a fila. NUNCA prometa tempo exato nem "
                + "'você é o próximo garantido'. Você NÃO chama o cliente — quem chama é o barbeiro no "
                + "balcão; você só ENFILEIRA e INFORMA a posição/espera estimadas.\n")
            .append("Quando MARCAR HORÁRIO estiver definido (barbeiro + serviço + dia + hora), sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<agendamento_barbearia>{\"barber_id\":\"UUID\",\"service_id\":\"UUID\","
                + "\"date\":\"YYYY-MM-DD\",\"start_time\":\"HH:MM\",\"notes\":\"...\","
                + "\"cupom\":\"CODIGO ou omitido\"}</agendamento_barbearia>\n")
            .append("Se o cliente informar um CUPOM de desconto, inclua o código no campo \"cupom\" — quem "
                + "VALIDA e CALCULA o desconto é o sistema; você NUNCA confirma valor de desconto por conta "
                + "própria (se o cupom for inválido, o agendamento sai sem desconto).\n")
            .append("Quando ENTRAR NA FILA estiver definido (serviço + opcionalmente um barbeiro), sua "
                + "ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<fila_barbearia>{\"service_id\":\"UUID\",\"barber_id\":\"UUID ou null\","
                + "\"notes\":\"...\"}</fila_barbearia>\n")
            .append("Use os ids EXATOS das listas acima. barber_id null na fila = qualquer barbeiro. Só "
                + "emita a tag na confirmação final.\n");
        if (config.upsellEnabled()) {
            sb.append("UPSELL LIGADO (onda 1, backlog #4): no FECHAMENTO do agendamento você PODE sugerir "
                + "UMA ÚNICA vez incluir um serviço complementar DA LISTA acima (ex.: barba, sobrancelha), "
                + "citando a duração/preço do catálogo; se o cliente recusar ou ignorar, NÃO insista.\n");
        }
        sb.append("\n");

        return sb.toString();
    }

    /** Contagem de tickets 'aguardando' por barbeiro (chave null = fila geral). */
    private Map<UUID, Integer> waitingCounts(UUID companyId) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (BarberQueueTicket t : queueRepository.listWaiting(companyId)) {
            counts.merge(t.barberId(), 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Slots livres por barbeiro nos próximos {@value #CONTEXT_DAYS} dias. Para cada barbeiro, para cada
     * dia, gera slots de config.slotMinutes entre opens/closes e remove os ocupados por agendamentos
     * ATIVOS daquele barbeiro. Lista resumida.
     */
    private String buildSlotsSegment(UUID companyId, BarberConfig config, List<BarberBarber> barbers,
                                     List<BarberService> services) {
        StringBuilder sb = new StringBuilder("HORÁRIOS LIVRES (próximos ")
            .append(CONTEXT_DAYS).append(" dias, por barbeiro):\n");
        if (barbers.isEmpty() || services.isEmpty()) {
            sb.append("(sem barbeiros ou serviços ativos — não há disponibilidade.)\n\n");
            return sb.toString();
        }
        int slotMin = config.slotMinutes();
        Instant now = Instant.now();
        Instant until = now.plus(Duration.ofDays(CONTEXT_DAYS));
        ZonedDateTime startDay = now.atZone(TENANT_ZONE).toLocalDate().atStartOfDay(TENANT_ZONE);

        for (BarberBarber p : barbers) {
            List<BarberAppointment> active =
                appointmentRepository.listActiveByBarber(companyId, p.id(), now, until);
            List<String> dayChunks = new ArrayList<>();
            for (int d = 0; d < CONTEXT_DAYS && dayChunks.size() < CONTEXT_DAYS; d++) {
                LocalDate day = startDay.plusDays(d).toLocalDate();
                List<String> free = new ArrayList<>();
                LocalTime t = config.opensAt();
                while (t.plusMinutes(slotMin).compareTo(config.closesAt()) <= 0
                        && free.size() < MAX_SLOTS_PER_BARBER_DAY) {
                    ZonedDateTime slotStart = day.atTime(t).atZone(TENANT_ZONE);
                    ZonedDateTime slotEnd = slotStart.plusMinutes(slotMin);
                    boolean inFuture = slotStart.toInstant().isAfter(now);
                    boolean occupied = active.stream().anyMatch(a ->
                        !(a.endAt().compareTo(slotStart.toInstant()) <= 0
                            || a.startAt().compareTo(slotEnd.toInstant()) >= 0));
                    if (inFuture && !occupied) {
                        free.add(TIME_FMT.format(t));
                    }
                    t = t.plusMinutes(slotMin);
                }
                if (!free.isEmpty()) {
                    dayChunks.add(DATE_FMT.format(day.atStartOfDay(TENANT_ZONE)) + " " + String.join(", ", free));
                }
            }
            if (!dayChunks.isEmpty()) {
                sb.append("- ").append(p.name()).append(": ")
                    .append(String.join(" | ", dayChunks)).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
