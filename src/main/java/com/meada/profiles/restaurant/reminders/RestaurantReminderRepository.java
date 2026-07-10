package com.meada.profiles.restaurant.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras do RestaurantReminderJob (onda 1, backlog #1/#3). service_role, cruza TODOS os
 * tenants restaurant numa query só, respeitando os toggles da config (ausência de linha = default
 * ligado). Datas do lembrete comparadas no fuso America/Sao_Paulo (o "amanhã" do restaurante).
 */
@Repository
public class RestaurantReminderRepository {

    private static final RowMapper<DueReservation> MAPPER = (rs, rn) -> new DueReservation(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("guest_name"),
        rs.getString("table_label"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getInt("num_people"));

    private static final String SELECT =
        "select r.id, r.company_id, r.conversation_id, r.guest_name, t.label as table_label, "
            + "r.start_at, r.num_people "
            + "from table_reservations r "
            + "join restaurant_tables t on t.id = r.table_id "
            + "join companies c on c.id = r.company_id "
            + "left join restaurant_reservation_config cfg on cfg.company_id = r.company_id ";

    private final JdbcTemplate jdbcTemplate;

    public RestaurantReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** #1 — reservas pendente/confirmada de {@code targetDate} ainda não lembradas (toggle ligado). */
    public List<DueReservation> findDueReminders(LocalDate targetDate) {
        return jdbcTemplate.query(
            SELECT
                + "where c.profile_id = 'restaurant' "
                + "and coalesce(cfg.reminder_enabled, true) "
                + "and r.status in ('pendente','confirmada') "
                + "and r.reminded_24h = false "
                + "and (r.start_at at time zone 'America/Sao_Paulo')::date = ? "
                + "order by r.company_id, r.start_at",
            MAPPER, Date.valueOf(targetDate));
    }

    /** Marca o lembrete como enviado (idempotência — inclusive sem canal resolúvel). */
    public void markReminded(UUID reservationId) {
        jdbcTemplate.update(
            "update table_reservations set reminded_24h = true where id = ?", reservationId);
    }

    /** #3 — confirmadas cujo end_at passou do corte (folga), com a auto-transição LIGADA. */
    public List<DueReservation> findConfirmedPast(Instant cutoff) {
        return jdbcTemplate.query(
            SELECT
                + "where c.profile_id = 'restaurant' "
                + "and coalesce(cfg.auto_complete_enabled, true) "
                + "and r.status = 'confirmada' "
                + "and r.end_at < ? "
                + "order by r.company_id, r.end_at",
            MAPPER, Timestamp.from(cutoff));
    }
}
