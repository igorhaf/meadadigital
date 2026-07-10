package com.meada.profiles.casamento.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.casamento.proposals.WeddingProposalNotifier;
import com.meada.profiles.casamento.proposals.WeddingProposalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Auto-transição + aniversário do casamento (onda 1 do backlog #4/#16).
 *
 * <p>Diariamente (cron): (a) proposta FECHADA cujo {@code wedding_date} já passou vira REALIZADA
 * via {@link WeddingProposalService#updateStatus} (transição válida da máquina; 'realizada' é
 * SILENCIOSA — quem casou não recebe aviso burocrático) — toggle {@code auto_complete_enabled};
 * (b) no dia/mês do wedding_date de proposta REALIZADA (1+ ano), parabeniza o casal 1x/ano
 * ({@code anniversary_notified_year}) — toggle {@code anniversary_enabled}. Texto fixo e
 * defensivo, sem oferta agressiva; sem canal → marca sem enviar (não revarre).
 */
@Component
public class WeddingAutoTransitionJob {

    private static final Logger log = LoggerFactory.getLogger(WeddingAutoTransitionJob.class);

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final WeddingReminderRepository reminderRepository;
    private final WeddingProposalService proposalService;
    private final WeddingProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public WeddingAutoTransitionJob(WeddingReminderRepository reminderRepository,
                                    WeddingProposalService proposalService,
                                    WeddingProposalNotifier notifier,
                                    ScheduledJobRunRepository jobRunRepository,
                                    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.reminderRepository = reminderRepository;
        this.proposalService = proposalService;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Tick agendado (cron configurável; default diário às 8h). Delega ao método público p/ testes. */
    @Scheduled(cron = "${casamento.auto-transition-cron:0 0 8 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("WeddingAutoTransitionJob");
        try {
            runAutoTransitions();
            runFollowUps();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /**
     * Aplica auto-realizada (#4) + aniversário (#16) em todos os tenants casamento. Público e
     * direto para os testes.
     *
     * @return número de propostas tocadas (realizadas + parabenizadas)
     */
    public int runAutoTransitions() {
        LocalDate today = LocalDate.now(TENANT_ZONE);
        int touched = 0;

        // (a) fechada com a festa já acontecida → realizada (silencioso; notifica nada).
        List<WeddingReminderRepository.CompletableProposal> completable =
            reminderRepository.findCompletableProposals(today);
        for (WeddingReminderRepository.CompletableProposal p : completable) {
            try {
                proposalService.updateStatus(p.companyId(), p.proposalId(), "realizada");
                touched++;
            } catch (RuntimeException e) {
                log.warn("casamento-auto: realizar proposta {} falhou ({})", p.proposalId(), e.getMessage());
            }
        }

        // (b) aniversário de casamento — 1 parabéns por ano.
        List<WeddingReminderRepository.AnniversaryProposal> anniversaries =
            reminderRepository.findAnniversaries(today);
        for (WeddingReminderRepository.AnniversaryProposal a : anniversaries) {
            try {
                if (a.conversationId() == null) {
                    log.info("casamento-auto: aniversário da proposta {} sem canal — marcado sem envio",
                        a.proposalId());
                } else {
                    notifier.notifyStatus(a.companyId(), a.conversationId(),
                        "Feliz aniversário de casamento! 🥂 A equipe que cuidou do grande dia de vocês "
                            + "manda um abraço — contem com a gente sempre que quiserem celebrar de novo.");
                }
                reminderRepository.markAnniversaryNotified(a.proposalId(), today.getYear());
                touched++;
            } catch (Exception e) {
                log.warn("casamento-auto: aniversário da proposta {} falhou ({})", a.proposalId(), e.getMessage());
            }
        }
        return touched;
    }

    /**
     * Onda 2 (backlog #8): follow-up de proposta ORCADA parada — 1 toque gentil por episódio
     * (follow_up_sent_at vs status_updated_at; re-orçar REARMA). Funil ativo → default ON.
     * Público para os testes.
     */
    public int runFollowUps() {
        record Due(java.util.UUID id, java.util.UUID companyId, java.util.UUID conversationId,
                   java.sql.Date eventDate) {}
        java.util.List<Due> due = jdbcTemplate.query(
            "select p.id, p.company_id, p.conversation_id, p.wedding_date "
                + "from wedding_proposals p "
                + "join companies co on co.id = p.company_id and co.profile_id = 'casamento' "
                + "left join wedding_config cfg on cfg.company_id = p.company_id "
                + "where coalesce(cfg.follow_up_enabled, true) "
                + "and p.status = 'orcada' "
                + "and p.status_updated_at < now() - make_interval(days => coalesce(cfg.follow_up_days, 5)) "
                + "and (p.follow_up_sent_at is null or p.follow_up_sent_at < p.status_updated_at) "
                + "order by p.company_id",
            (rs, rn) -> new Due((java.util.UUID) rs.getObject("id"),
                (java.util.UUID) rs.getObject("company_id"),
                (java.util.UUID) rs.getObject("conversation_id"), rs.getDate("wedding_date")));
        int touched = 0;
        for (Due d : due) {
            try {
                StringBuilder sb = new StringBuilder("Oi! O orçamento do casamento de vocês");
                if (d.eventDate() != null) {
                    sb.append(" (").append(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        .format(d.eventDate().toLocalDate())).append(")");
                }
                sb.append(" continua reservado por aqui. Ficou alguma dúvida? Estamos à disposição "
                    + "pra ajustar o que precisar. 💐");
                notifier.notifyStatus(d.companyId(), d.conversationId(), sb.toString());
                jdbcTemplate.update(
                    "update wedding_proposals set follow_up_sent_at = now() where id = ?", d.id());
                touched++;
            } catch (Exception e) {
                log.warn("casamento-followup: failed {} ({})", d.id(), e.getMessage());
            }
        }
        return touched;
    }

}
