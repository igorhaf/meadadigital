package com.meada.profiles.viagens.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.viagens.proposals.TravelProposalNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Disparos temporais da onda Viagens (backlog docs/FEATURES_SUGERIDAS_VIAGENS.md #2 e #8):
 * lembretes de viagem (D-7 checklist, D0 boa viagem, D+2 pós-viagem/NPS das propostas FECHADAS
 * com datas) e follow-up gentil da proposta ORÇADA parada. A agência vive de recompra/indicação —
 * o pré e o pós-viagem são os momentos de ouro que hoje eram desperdiçados.
 *
 * <p>Mensagens FIXAS e defensivas pela conversa da proposta ({@link TravelProposalNotifier}) — NÃO
 * passam pela IA (trava do nicho: nada de confirmar voo/hotel/preço; o follow-up só transmite o
 * total JÁ orçado pela equipe). Sem canal (proposta manual) → marca sem envio, evitando revarredura
 * eterna. {@code EVOLUTION_DRY_RUN} honrado por baixo do notifier.
 *
 * <p>Idempotência: por (proposta, data) nos lembretes — remarcar a viagem REARMA; por episódio de
 * 'orcada' no follow-up — re-orçar rearma. Molde dos jobs existentes: {@code @Scheduled} fino
 * instrumentado via {@link ScheduledJobRunRepository}; lógica em métodos públicos testáveis.
 */
@Component
public class TravelReminderJob {

    private static final Logger log = LoggerFactory.getLogger(TravelReminderJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final TravelReminderRepository reminderRepository;
    private final TravelProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public TravelReminderJob(TravelReminderRepository reminderRepository,
                             TravelProposalNotifier notifier,
                             ScheduledJobRunRepository jobRunRepository) {
        this.reminderRepository = reminderRepository;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 9h). Delega aos públicos para os testes. */
    @Scheduled(cron = "${viagens.trip-reminder-cron:0 10 9 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("TravelReminderJob");
        try {
            runTripReminders();
            runQuoteFollowups();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Dispara os três lembretes de viagem do dia (D-7, D0 e D+2 pós-viagem). Público para os testes.
     *
     * @return número de propostas marcadas neste run (com ou sem canal)
     */
    public int runTripReminders() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;
        touched += sweep(reminderRepository.findPretripDue(today.plusDays(7)),
            t -> "Olá, " + t.customerName() + "! Faltam 7 dias para sua " + label(t) + " (embarque "
                + DAY_MONTH.format(t.startDate()) + "). Vale conferir: documentos, reservas impressas "
                + "e bagagem. Qualquer dúvida, estamos por aqui! ✈️",
            (id, t) -> reminderRepository.markPretripReminded(id, t.startDate()));
        touched += sweep(reminderRepository.findStartDue(today),
            t -> "Chegou o grande dia, " + t.customerName() + "! Boa " + label(t)
                + " — aproveite cada momento. Se precisar de algo durante a viagem, é só chamar. 🌎",
            (id, t) -> reminderRepository.markStartReminded(id, t.startDate()));
        touched += sweep(reminderRepository.findPosttripDue(today.minusDays(2)),
            t -> "Bem-vindo(a) de volta, " + t.customerName() + "! Como foi a " + label(t)
                + "? Adoraríamos saber sua opinião — e se conhecer alguém planejando viajar, "
                + "indica a gente? 💙",
            (id, t) -> reminderRepository.markPosttripReminded(id, t.endDate()));
        return touched;
    }

    /**
     * Cutuca as propostas orçadas paradas (janela por tenant na config). Público para os testes.
     *
     * @return número de propostas cutucadas neste run (com ou sem canal)
     */
    public int runQuoteFollowups() {
        List<DueQuote> due = reminderRepository.findQuoteFollowupsDue();
        int touched = 0;
        for (DueQuote q : due) {
            try {
                if (q.conversationId() == null) {
                    log.info("viagens-followup: proposta {} sem canal (manual) — marcada sem envio",
                        q.proposalId());
                } else {
                    notifier.notifyStatus(q.companyId(), q.conversationId(),
                        "Olá, " + q.customerName() + "! Sua cotação"
                            + (q.destination() == null || q.destination().isBlank()
                                ? "" : " para " + q.destination())
                            + " está pronta (" + brl(q.totalCents()) + ") e seguimos à disposição. "
                            + "Ficou alguma dúvida? Podemos ajustar o roteiro como você preferir. 😊");
                }
                reminderRepository.markQuoteFollowedUp(q.proposalId());
                touched++;
            } catch (Exception e) {
                log.warn("viagens-followup: failed proposal {} ({})", q.proposalId(), e.getMessage());
            }
        }
        return touched;
    }

    private int sweep(List<DueTrip> due, Function<DueTrip, String> text,
                      BiConsumer<java.util.UUID, DueTrip> mark) {
        int touched = 0;
        for (DueTrip t : due) {
            try {
                if (t.conversationId() == null) {
                    log.info("viagens-reminder: proposta {} sem canal (manual) — marcada sem envio",
                        t.proposalId());
                } else {
                    // best-effort: falha de envio loga no notifier e NÃO impede a marcação.
                    notifier.notifyStatus(t.companyId(), t.conversationId(), text.apply(t));
                }
                mark.accept(t.proposalId(), t);
                touched++;
            } catch (Exception e) {
                log.warn("viagens-reminder: failed proposal {} ({})", t.proposalId(), e.getMessage());
            }
        }
        return touched;
    }

    private static String label(DueTrip t) {
        return t.destination() == null || t.destination().isBlank()
            ? "viagem" : "viagem para " + t.destination();
    }

    private static String brl(int cents) {
        return "R$ " + String.format("%d,%02d", cents / 100, cents % 100);
    }
}
