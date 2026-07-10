package com.meada.profiles.las.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import com.meada.profiles.las.orders.LasOrderNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Reativação de cliente inativo do lãs (onda 1, backlog #7 — "chegaram lotes novos"). Quem tricota
 * é sazonal: projeto puxa projeto, mas a base esfria em silêncio. Diariamente varre os contatos
 * dos tenants lãs com o OPT-IN ligado ({@code reactivation_enabled}, default DESLIGADO — lição
 * Baileys) cujo último pedido ENTREGUE é anterior à janela, e envia UMA mensagem pela conversa
 * mais recente, citando o cupom de retorno só quando existe/ativo/válido. Cooldown = a própria
 * janela ({@code las_reactivation_log}); sem canal → marca sem envio.
 */
@Component
public class LasReactivationJob {

    private static final Logger log = LoggerFactory.getLogger(LasReactivationJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final LasOrderNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public LasReactivationJob(JdbcTemplate jdbcTemplate, LasOrderNotifier notifier,
                              ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 10h40). Delega ao público p/ os testes. */
    @Scheduled(cron = "${las.reactivation-cron:0 40 10 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("LasReactivationJob");
        try {
            runReactivation();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** Reaborda os inativos due de todos os tenants lãs. Público e direto para os testes. */
    public int runReactivation() {
        record Due(UUID companyId, UUID contactId, String contactName, UUID conversationId,
                   String couponCode) {}
        List<Due> due = jdbcTemplate.query(
            "select ct.company_id, ct.id as contact_id, ct.name as contact_name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = ct.company_id and conv.contact_id = ct.id "
                + "  order by conv.created_at desc limit 1) as conversation_id, "
                + "cp.code as coupon_code "
                + "from contacts ct "
                + "join companies co on co.id = ct.company_id and co.profile_id = 'las' "
                + "join las_config cfg on cfg.company_id = ct.company_id and cfg.reactivation_enabled "
                + "left join las_coupons cp on cp.company_id = ct.company_id "
                + "  and cfg.reactivation_coupon_code is not null "
                + "  and lower(cp.code) = lower(cfg.reactivation_coupon_code) "
                + "  and cp.active "
                + "  and (cp.valid_until is null or cp.valid_until >= current_date) "
                + "  and (cp.max_uses is null or cp.uses < cp.max_uses) "
                + "where (select max(o.created_at) from las_orders o "
                + "         where o.company_id = ct.company_id and o.contact_id = ct.id "
                + "         and o.status = 'entregue') "
                + "      < now() - make_interval(days => cfg.reactivation_days) "
                + "and not exists (select 1 from las_reactivation_log l "
                + "  where l.company_id = ct.company_id and l.contact_id = ct.id "
                + "  and l.sent_at > now() - make_interval(days => cfg.reactivation_days)) "
                + "order by ct.company_id",
            (rs, rn) -> new Due(
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("contact_id"),
                rs.getString("contact_name"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("coupon_code")));
        int touched = 0;
        for (Due c : due) {
            try {
                if (c.conversationId() == null) {
                    log.info("las-reactivation: contato {} sem conversa — marcado sem envio", c.contactId());
                    markSent(c.companyId(), c.contactId(), false);
                } else {
                    String nome = c.contactName() == null || c.contactName().isBlank()
                        ? "" : ", " + c.contactName();
                    StringBuilder sb = new StringBuilder("Oi").append(nome)
                        .append("! Chegaram lotes novos por aqui 🧶 Que tal retomar aquele projeto?");
                    if (c.couponCode() != null) {
                        sb.append(" Use o cupom ").append(c.couponCode()).append(" no seu próximo pedido.");
                    }
                    sb.append(" É só chamar!");
                    notifier.notifyStatus(c.companyId(), c.conversationId(), sb.toString());
                    markSent(c.companyId(), c.contactId(), true);
                }
                touched++;
            } catch (Exception e) {
                log.warn("las-reactivation: failed contact {} ({})", c.contactId(), e.getMessage());
            }
        }
        return touched;
    }

    private void markSent(UUID companyId, UUID contactId, boolean hadChannel) {
        jdbcTemplate.update(
            "insert into las_reactivation_log (company_id, contact_id, had_channel) values (?, ?, ?)",
            companyId, contactId, hadChannel);
    }
}
