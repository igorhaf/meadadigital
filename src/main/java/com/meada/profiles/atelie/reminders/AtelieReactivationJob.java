package com.meada.profiles.atelie.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.atelie.proposals.AtelieProposalNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reativação de cliente inativo do ateliê (onda 3, backlog #3): peça sob medida puxa a próxima
 * (ocasião nova, estação nova), mas a base esfria em silêncio. Diariamente varre os contatos dos
 * tenants atelie com o OPT-IN ligado ({@code reactivation_enabled}, default DESLIGADO — lição
 * Baileys) cuja última proposta REALIZADA é anterior à janela e sem proposta ativa, e envia UMA
 * mensagem pela conversa mais recente. Cooldown = a própria janela ({@code
 * atelie_reactivation_log}); sem canal → marca sem envio. Best-effort.
 */
@Component
public class AtelieReactivationJob {

    private static final Logger log = LoggerFactory.getLogger(AtelieReactivationJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final AtelieProposalNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public AtelieReactivationJob(JdbcTemplate jdbcTemplate, AtelieProposalNotifier notifier,
                                 ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 11h20). Delega ao público p/ os testes. */
    @Scheduled(cron = "${atelie.reactivation-cron:0 20 11 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("AtelieReactivationJob");
        try {
            runReactivation();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Reaborda os inativos due de todos os tenants atelie. Público e direto para os testes. */
    public int runReactivation() {
        record Due(UUID companyId, UUID contactId, String contactName, UUID conversationId) {}
        List<Due> due = jdbcTemplate.query(
            "select ct.company_id, ct.id as contact_id, ct.name as contact_name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = ct.company_id and conv.contact_id = ct.id "
                + "  order by conv.created_at desc limit 1) as conversation_id "
                + "from contacts ct "
                + "join companies co on co.id = ct.company_id and co.profile_id = 'atelie' "
                + "join atelie_config cfg on cfg.company_id = ct.company_id and cfg.reactivation_enabled "
                + "where (select max(p.opened_at) from atelie_proposals p "
                + "         where p.company_id = ct.company_id and p.contact_id = ct.id "
                + "         and p.status = 'realizada') "
                + "      < now() - make_interval(days => cfg.reactivation_days) "
                + "and not exists (select 1 from atelie_proposals a "
                + "  where a.company_id = ct.company_id and a.contact_id = ct.id "
                + "  and a.status in ('rascunho','orcada','aprovada','fechada')) "
                + "and not exists (select 1 from atelie_reactivation_log l "
                + "  where l.company_id = ct.company_id and l.contact_id = ct.id "
                + "  and l.sent_at > now() - make_interval(days => cfg.reactivation_days)) "
                + "order by ct.company_id",
            (rs, rn) -> new Due(
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("contact_id"),
                rs.getString("contact_name"),
                (UUID) rs.getObject("conversation_id")));
        int touched = 0;
        for (Due c : due) {
            try {
                if (c.conversationId() == null) {
                    log.info("atelie-reactivation: contato {} sem conversa — marcado sem envio", c.contactId());
                    markSent(c.companyId(), c.contactId(), false);
                } else {
                    String nome = c.contactName() == null || c.contactName().isBlank()
                        ? "" : ", " + c.contactName();
                    notifier.notifyStatus(c.companyId(), c.conversationId(),
                        "Oi" + nome + "! Faz um tempinho desde a sua última peça com a gente 🪡 "
                            + "Se estiver pensando em algo novo — uma ocasião especial, um ajuste, "
                            + "uma ideia — é só me chamar por aqui!");
                    markSent(c.companyId(), c.contactId(), true);
                }
                touched++;
            } catch (Exception e) {
                log.warn("atelie-reactivation: failed contact {} ({})", c.contactId(), e.getMessage());
            }
        }
        return touched;
    }

    private void markSent(UUID companyId, UUID contactId, boolean hadChannel) {
        jdbcTemplate.update(
            "insert into atelie_reactivation_log (company_id, contact_id, had_channel) values (?, ?, ?)",
            companyId, contactId, hadChannel);
    }
}
