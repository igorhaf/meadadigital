package com.meada.profiles.pousada.reminders;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Varreduras do PousadaReminderJob (onda 1, backlog #2/#4). service_role, cruza TODOS os tenants
 * pousada numa query só, respeitando os toggles da config (ausência de linha = reminder ligado,
 * auto-transição DESLIGADA). Datas são DATE puro (o chassi da pousada é intervalo de dias).
 */
@Repository
public class PousadaReminderRepository {

    private static final RowMapper<DuePousadaReservation> MAPPER = (rs, rn) -> new DuePousadaReservation(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("guest_name"),
        rs.getString("room_name"),
        rs.getDate("check_in_date").toLocalDate(),
        rs.getDate("check_out_date").toLocalDate());

    private static final String SELECT =
        "select r.id, r.company_id, r.conversation_id, r.guest_name, r.room_name, "
            + "r.check_in_date, r.check_out_date "
            + "from pousada_reservations r "
            + "join companies c on c.id = r.company_id "
            + "left join pousada_config cfg on cfg.company_id = r.company_id ";

    private final JdbcTemplate jdbcTemplate;

    public PousadaReminderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** #2 — reservado/confirmado com check-in em {@code targetDate}, não lembrados pra ESSA data. */
    public List<DuePousadaReservation> findDueReminders(LocalDate targetDate) {
        return jdbcTemplate.query(
            SELECT
                + "where c.profile_id = 'pousada' "
                + "and coalesce(cfg.reminder_enabled, true) "
                + "and r.status in ('reservado','confirmado') "
                + "and r.check_in_date = ? "
                + "and (r.reminded_checkin_date is null or r.reminded_checkin_date <> r.check_in_date) "
                + "order by r.company_id",
            MAPPER, Date.valueOf(targetDate));
    }

    /** Marca o check_in_date como lembrado (idempotência por reserva+data; remarcar rearma). */
    public void markReminded(UUID reservationId, LocalDate checkInDate) {
        jdbcTemplate.update(
            "update pousada_reservations set reminded_checkin_date = ? where id = ?",
            Date.valueOf(checkInDate), reservationId);
    }

    /** #4 — confirmado com check-in vencido (folga de 1 dia), auto-transição LIGADA (opt-in). */
    public List<DuePousadaReservation> findConfirmedPastCheckin(LocalDate today) {
        return jdbcTemplate.query(
            SELECT
                + "where c.profile_id = 'pousada' "
                + "and coalesce(cfg.auto_transition_enabled, false) "
                + "and r.status = 'confirmado' "
                + "and r.check_in_date < ? "
                + "order by r.company_id",
            MAPPER, Date.valueOf(today.minusDays(1)));
    }

    /** #4 — checked_in com check-out vencido, auto-transição LIGADA (opt-in). */
    public List<DuePousadaReservation> findCheckedInPastCheckout(LocalDate today) {
        return jdbcTemplate.query(
            SELECT
                + "where c.profile_id = 'pousada' "
                + "and coalesce(cfg.auto_transition_enabled, false) "
                + "and r.status = 'checked_in' "
                + "and r.check_out_date < ? "
                + "order by r.company_id",
            MAPPER, Date.valueOf(today));
    }
}
