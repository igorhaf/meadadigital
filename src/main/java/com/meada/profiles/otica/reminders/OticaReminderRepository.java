package com.meada.profiles.otica.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras do OticaReminderJob (onda 1, backlog #1/#2). service_role, cruza TODOS os tenants
 * otica numa query só (toggles na config; ausência de linha = ligados). Datas no fuso
 * America/Sao_Paulo.
 */
@Repository
public class OticaReminderRepository {

    private static final RowMapper<DueOticaWork> EXAM_MAPPER = (rs, rn) -> new DueOticaWork(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("customer_name"),
        rs.getString("professional_name"),
        rs.getTimestamp("start_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public OticaReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** #1 — exames de {@code targetDate} ainda não lembrados pro start_at atual (remarcar rearma). */
    public List<DueOticaWork> findDueExamReminders(LocalDate targetDate) {
        return jdbcTemplate.query(
            "select e.id, e.company_id, e.conversation_id, e.customer_name, e.professional_name, e.start_at "
                + "from otica_exam_appointments e "
                + "join companies c on c.id = e.company_id "
                + "left join otica_config cfg on cfg.company_id = e.company_id "
                + "where c.profile_id = 'otica' "
                + "and coalesce(cfg.exam_reminder_enabled, true) "
                + "and e.status in ('agendado','confirmado') "
                + "and (e.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "and (e.reminded_start_at is null or e.reminded_start_at <> e.start_at) "
                + "order by e.company_id, e.start_at",
            EXAM_MAPPER, Date.valueOf(targetDate));
    }

    public void markExamReminded(UUID examId, Instant startAt) {
        jdbcTemplate.update(
            "update otica_exam_appointments set reminded_start_at = ? where id = ?",
            java.sql.Timestamp.from(startAt), examId);
    }

    /** #2 — pedidos parados em 'pronto' além da janela, sem follow-up DESTE episódio. */
    public List<DueOticaWork> findStalePickups() {
        return jdbcTemplate.query(
            "select o.id, o.company_id, o.conversation_id, ct.name as customer_name, "
                + "null as professional_name, o.status_updated_at as start_at "
                + "from otica_orders o "
                + "join contacts ct on ct.id = o.contact_id "
                + "join companies c on c.id = o.company_id "
                + "left join otica_config cfg on cfg.company_id = o.company_id "
                + "where c.profile_id = 'otica' "
                + "and coalesce(cfg.pickup_followup_enabled, true) "
                + "and o.status = 'pronto' "
                + "and o.status_updated_at <= now() - make_interval(days => coalesce(cfg.pickup_followup_days, 3)) "
                + "and (o.pickup_followup_sent_at is null or o.pickup_followup_sent_at < o.status_updated_at) "
                + "order by o.company_id",
            EXAM_MAPPER);
    }

    public void markPickupFollowedUp(UUID orderId) {
        jdbcTemplate.update(
            "update otica_orders set pickup_followup_sent_at = now() where id = ?", orderId);
    }
}
