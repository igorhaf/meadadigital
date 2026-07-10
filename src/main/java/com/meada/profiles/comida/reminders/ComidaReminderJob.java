package com.meada.profiles.comida.reminders;

import com.meada.admin.health.ScheduledJobRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Automações da onda Comida 2 num tick diário (backlog #5/#12): auto-entrega de pedido em
 * saiu_entrega há mais de auto_deliver_hours (opt-in por config — NULL desliga; silencioso) e
 * REATIVAÇÃO de inativo (opt-in OFF — lição Baileys), com cupom de retorno opcional. O alerta de
 * pedido parado em aguardando/em_preparo é DERIVADO no painel (badge por status_updated_at, sem
 * job — cancelamento/aceite são humanos). Best-effort.
 */
@Component
public class ComidaReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ComidaReminderJob.class);

    private final JdbcTemplate jdbcTemplate;
    private final com.meada.profiles.comida.orders.ComidaOrderNotifier notifier;
    private final ScheduledJobRunRepository jobRunRepository;

    public ComidaReminderJob(JdbcTemplate jdbcTemplate,
                             com.meada.profiles.comida.orders.ComidaOrderNotifier notifier,
                             ScheduledJobRunRepository jobRunRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
        this.jobRunRepository = jobRunRepository;
    }

    /** Tick agendado (cron configurável; default diário às 11h50). Delega aos públicos p/ os testes. */
    @Scheduled(cron = "${comida.reminder-cron:0 50 11 * * *}")
    public void scheduledRun() {
        var runId = jobRunRepository.start("ComidaReminderJob");
        try {
            runAutoDeliver();
            runReactivations();
            jobRunRepository.finishSuccess(runId);
        } catch (RuntimeException e) {
            jobRunRepository.finishFailed(runId, e.getMessage());
            throw e;
        }
    }

    /** saiu_entrega além de auto_deliver_hours → entregue (silencioso; opt-in). Público p/ testes. */
    public int runAutoDeliver() {
        return jdbcTemplate.update(
            "update comida_orders o set status = 'entregue', status_updated_at = now() "
                + "from companies co "
                + "join comida_config cfg on cfg.company_id = co.id "
                + "where co.id = o.company_id and co.profile_id = 'comida' "
                + "and cfg.auto_deliver_hours is not null "
                + "and o.status = 'saiu_entrega' "
                + "and o.status_updated_at < now() - make_interval(hours => cfg.auto_deliver_hours)");
    }

    /** Reativação de inativos (#5, opt-in OFF). Cooldown = janela. Público p/ testes. */
    public int runReactivations() {
        record Due(UUID companyId, UUID contactId, String contactName, UUID conversationId,
                   String couponCode) {}
        List<Due> due = jdbcTemplate.query(
            "select ct.company_id, ct.id as contact_id, ct.name as contact_name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = ct.company_id and conv.contact_id = ct.id "
                + "  order by conv.created_at desc limit 1) as conversation_id, "
                + "cp.code as coupon_code "
                + "from contacts ct "
                + "join companies co on co.id = ct.company_id and co.profile_id = 'comida' "
                + "join comida_config cfg on cfg.company_id = ct.company_id and cfg.reactivation_enabled "
                + "left join comida_coupons cp on cp.company_id = ct.company_id "
                + "  and cfg.reactivation_coupon_code is not null "
                + "  and lower(cp.code) = lower(cfg.reactivation_coupon_code) "
                + "  and cp.active "
                + "  and (cp.valid_until is null or cp.valid_until >= current_date) "
                + "  and (cp.max_uses is null or cp.uses < cp.max_uses) "
                + "where (select max(o.created_at) from comida_orders o "
                + "         where o.company_id = ct.company_id and o.contact_id = ct.id "
                + "         and o.status = 'entregue') "
                + "      < now() - make_interval(days => cfg.reactivation_days) "
                + "and not exists (select 1 from comida_reactivation_log l "
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
                    log.info("comida-reactivation: contato {} sem conversa — marcado sem envio", c.contactId());
                    markSent(c.companyId(), c.contactId(), false);
                } else {
                    String nome = c.contactName() == null || c.contactName().isBlank()
                        ? "" : ", " + c.contactName();
                    StringBuilder sb = new StringBuilder("Oi").append(nome)
                        .append("! Sentimos sua falta por aqui 🍔 Bateu a fome? Bora matar a saudade?");
                    if (c.couponCode() != null) {
                        sb.append(" Use o cupom ").append(c.couponCode()).append(" no seu próximo pedido.");
                    }
                    sb.append(" É só chamar!");
                    notifier.notifyStatus(c.companyId(), c.conversationId(), sb.toString());
                    markSent(c.companyId(), c.contactId(), true);
                }
                touched++;
            } catch (Exception e) {
                log.warn("comida-reactivation: failed contact {} ({})", c.contactId(), e.getMessage());
            }
        }
        return touched;
    }

    private void markSent(UUID companyId, UUID contactId, boolean hadChannel) {
        jdbcTemplate.update(
            "insert into comida_reactivation_log (company_id, contact_id, had_channel) values (?, ?, ?)",
            companyId, contactId, hadChannel);
    }
}
