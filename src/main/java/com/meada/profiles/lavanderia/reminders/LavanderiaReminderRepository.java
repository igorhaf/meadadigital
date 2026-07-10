package com.meada.profiles.lavanderia.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras da onda Lavanderia 1 (backlog #3/#7/#14). service_role; cruza TODOS os tenants
 * lavanderia numa query só, honrando os toggles da config (coalesce = default da migration).
 */
@Repository
public class LavanderiaReminderRepository {

    private final JdbcTemplate jdbcTemplate;

    public LavanderiaReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Pedido aguardando com coleta em {@code target} ainda não lembrado PARA ESSA data (#7). */
    public record DueCollect(UUID orderId, UUID companyId, UUID conversationId,
                             LocalDate collectDate, String period) {}

    public List<DueCollect> findDueCollectReminders(LocalDate target) {
        return jdbcTemplate.query(
            "select o.id, o.company_id, o.conversation_id, o.collect_date, o.period "
                + "from lavanderia_orders o "
                + "join companies co on co.id = o.company_id and co.profile_id = 'lavanderia' "
                + "left join lavanderia_config cfg on cfg.company_id = o.company_id "
                + "where coalesce(cfg.collect_reminder_enabled, true) "
                + "and o.status = 'aguardando' "
                + "and o.collect_date = ? "
                + "and (o.collect_reminded_date is null or o.collect_reminded_date <> o.collect_date) "
                + "order by o.company_id",
            (rs, rn) -> new DueCollect(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id"),
                rs.getDate("collect_date").toLocalDate(),
                rs.getString("period")),
            Date.valueOf(target));
    }

    public void markCollectReminded(UUID orderId, LocalDate collectDate) {
        jdbcTemplate.update(
            "update lavanderia_orders set collect_reminded_date = ? where id = ?",
            Date.valueOf(collectDate), orderId);
    }

    /** Pedido parado em 'pronto' além da janela, não lembrado NESTE episódio (#14). */
    public record DueReady(UUID orderId, UUID companyId, UUID conversationId) {}

    public List<DueReady> findDueReadyReminders() {
        return jdbcTemplate.query(
            "select o.id, o.company_id, o.conversation_id "
                + "from lavanderia_orders o "
                + "join companies co on co.id = o.company_id and co.profile_id = 'lavanderia' "
                + "left join lavanderia_config cfg on cfg.company_id = o.company_id "
                + "where coalesce(cfg.ready_reminder_enabled, true) "
                + "and o.status = 'pronto' "
                + "and o.status_updated_at < now() - make_interval(days => coalesce(cfg.ready_reminder_days, 2)) "
                + "and (o.ready_reminded_at is null or o.ready_reminded_at < o.status_updated_at) "
                + "order by o.company_id",
            (rs, rn) -> new DueReady(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("conversation_id")));
    }

    public void markReadyReminded(UUID orderId) {
        jdbcTemplate.update(
            "update lavanderia_orders set ready_reminded_at = now() where id = ?", orderId);
    }

    /**
     * Contatos inativos dos tenants com o OPT-IN ligado (#3, clone sushi): último pedido ENTREGUE
     * anterior à janela e sem disparo dentro dela ({@code lavanderia_reactivation_log}). O cupom de
     * retorno só entra quando existe/ativo/válido.
     */
    public record DueInactive(UUID companyId, UUID contactId, String contactName,
                              UUID conversationId, String couponCode) {}

    public List<DueInactive> findDueReactivations() {
        return jdbcTemplate.query(
            "select ct.company_id, ct.id as contact_id, ct.name as contact_name, "
                + "(select conv.id from conversations conv "
                + "  where conv.company_id = ct.company_id and conv.contact_id = ct.id "
                + "  order by conv.created_at desc limit 1) as conversation_id, "
                + "cp.code as coupon_code "
                + "from contacts ct "
                + "join companies co on co.id = ct.company_id and co.profile_id = 'lavanderia' "
                + "join lavanderia_config cfg on cfg.company_id = ct.company_id "
                + "  and cfg.reactivation_enabled "
                + "left join lavanderia_coupons cp on cp.company_id = ct.company_id "
                + "  and cfg.reactivation_coupon_code is not null "
                + "  and lower(cp.code) = lower(cfg.reactivation_coupon_code) "
                + "  and cp.active "
                + "  and (cp.valid_until is null or cp.valid_until >= current_date) "
                + "  and (cp.max_uses is null or cp.uses < cp.max_uses) "
                + "where (select max(o.created_at) from lavanderia_orders o "
                + "         where o.company_id = ct.company_id and o.contact_id = ct.id "
                + "         and o.status = 'entregue') "
                + "      < now() - make_interval(days => cfg.reactivation_days) "
                + "and not exists (select 1 from lavanderia_reactivation_log l "
                + "  where l.company_id = ct.company_id and l.contact_id = ct.id "
                + "  and l.sent_at > now() - make_interval(days => cfg.reactivation_days)) "
                + "order by ct.company_id",
            (rs, rn) -> new DueInactive(
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("contact_id"),
                rs.getString("contact_name"),
                (UUID) rs.getObject("conversation_id"),
                rs.getString("coupon_code")));
    }

    /** Registra o disparo (idempotência por contato+janela — inclusive sem canal resolúvel). */
    public void markReactivationSent(UUID companyId, UUID contactId, boolean hadChannel) {
        jdbcTemplate.update(
            "insert into lavanderia_reactivation_log (company_id, contact_id, had_channel) values (?, ?, ?)",
            companyId, contactId, hadChannel);
    }
}
