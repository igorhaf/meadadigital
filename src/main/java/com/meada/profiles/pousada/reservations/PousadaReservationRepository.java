package com.meada.profiles.pousada.reservations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code pousada_reservations} (camada 7.6). Opera via service_role.
 *
 * <p>EVOLUÇÃO ESTRUTURAL: o conflito é overlap de INTERVALOS DE DIAS (não slots de horas), por
 * QUARTO. {@link #findConflict} usa a condição HALF-OPEN {@code NOT (existing.check_out <=
 * new.check_in OR existing.check_in >= new.check_out)} — check-out de uma reserva e check-in de
 * outra NO MESMO DIA não conflitam (rotação). O {@link #insertReservation} re-verifica na transação
 * (defesa race) e materializa nights + total_cents + snapshots.
 */
@Repository
public class PousadaReservationRepository {

    private static final RowMapper<PousadaReservation> MAPPER = (rs, rn) -> new PousadaReservation(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("room_id"),
        rs.getString("room_name"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        rs.getInt("guests_count"),
        rs.getObject("check_in_date", LocalDate.class),
        rs.getObject("check_out_date", LocalDate.class),
        rs.getInt("nights"),
        rs.getInt("nightly_rate_cents"),
        rs.getInt("capacity_snapshot"),
        rs.getInt("total_cents"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, room_id, room_name, conversation_id, contact_id, guest_name, guest_phone, guests_count, "
            + "check_in_date, check_out_date, nights, nightly_rate_cents, capacity_snapshot, total_cents, "
            + "status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public PousadaReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PousadaReservation> listByCompany(UUID companyId, String status, LocalDate dateFrom,
                                                  LocalDate dateTo, UUID roomId, UUID contactId,
                                                  int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from pousada_reservations where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and check_in_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and check_in_date < ?"); args.add(Date.valueOf(dateTo)); }
        if (roomId != null) { sql.append(" and room_id = ?"); args.add(roomId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by check_in_date asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, LocalDate dateFrom, LocalDate dateTo,
                               UUID roomId, UUID contactId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from pousada_reservations where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and check_in_date >= ?"); args.add(Date.valueOf(dateFrom)); }
        if (dateTo != null) { sql.append(" and check_in_date < ?"); args.add(Date.valueOf(dateTo)); }
        if (roomId != null) { sql.append(" and room_id = ?"); args.add(roomId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<PousadaReservation> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from pousada_reservations where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Histórico do contato (mais recentes primeiro). */
    /** Próximas reservas ATIVAS do contato — bloco do contexto (onda 1, tag de confirmação). */
    public List<PousadaReservation> listUpcomingByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from pousada_reservations where company_id = ? and contact_id = ? "
                + "and status in ('reservado','confirmado') and check_in_date >= current_date "
                + "order by check_in_date asc limit " + Math.max(1, limit),
            MAPPER, companyId, contactId);
    }

    public List<PousadaReservation> listByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from pousada_reservations where company_id = ? and contact_id = ? "
                + "order by check_in_date desc limit ?",
            MAPPER, companyId, contactId, limit);
    }

    /** Reservas ATIVAS de um quarto na janela [from,to) — para a disponibilidade da IA. */
    public List<PousadaReservation> listActiveByRoom(UUID companyId, UUID roomId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
            "select " + COLS + " from pousada_reservations where company_id = ? and room_id = ? "
                + "and status in ('reservado','confirmado','checked_in') "
                + "and check_out_date > ? and check_in_date < ? order by check_in_date asc",
            MAPPER, companyId, roomId, Date.valueOf(from), Date.valueOf(to));
    }

    /**
     * Conflito de intervalo por QUARTO (decisão 5): reserva ATIVA (reservado/confirmado/checked_in)
     * do MESMO quarto cujo intervalo [check_in, check_out) sobrepõe [newCheckIn, newCheckOut).
     * Half-open: {@code NOT (existing.check_out_date <= newCheckIn OR existing.check_in_date >=
     * newCheckOut)} — rotação no mesmo dia (check_out == newCheckIn) NÃO conflita. Cálculo em SQL.
     */
    public Optional<PousadaReservationConflict> findConflict(UUID roomId, LocalDate newCheckIn, LocalDate newCheckOut) {
        return jdbcTemplate.query(
                "select id, guest_name, check_in_date, check_out_date, room_name "
                    + "from pousada_reservations "
                    + "where room_id = ? and status in ('reservado','confirmado','checked_in') "
                    + "and not (check_out_date <= ? or check_in_date >= ?) "
                    + "order by check_in_date asc limit 1",
                (rs, rn) -> new PousadaReservationConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("guest_name"),
                    rs.getObject("check_in_date", LocalDate.class),
                    rs.getObject("check_out_date", LocalDate.class),
                    rs.getString("room_name")),
                roomId, Date.valueOf(newCheckIn), Date.valueOf(newCheckOut))
            .stream().findFirst();
    }

    /**
     * Cria a reserva numa transação que RE-VERIFICA o conflito (por quarto) antes do insert
     * (decisão 5). nights e total_cents materializados; room_name/nightly_rate_cents/capacity
     * snapshots. Lança {@link DatesConflictException} se conflitar.
     */
    @Transactional
    public PousadaReservation insertReservation(UUID companyId, UUID roomId, String roomName,
                                                int nightlyRateCents, int capacitySnapshot,
                                                UUID conversationId, UUID contactId, String guestName,
                                                String guestPhone, int guestsCount, LocalDate checkIn,
                                                LocalDate checkOut, int nights, String notes) {
        Optional<PousadaReservationConflict> conflict = findConflict(roomId, checkIn, checkOut);
        if (conflict.isPresent()) {
            throw new DatesConflictException(conflict.get());
        }
        int totalCents = nightlyRateCents * nights;
        UUID id = jdbcTemplate.queryForObject(
            "insert into pousada_reservations (company_id, room_id, conversation_id, contact_id, "
                + "guest_name, guest_phone, guests_count, check_in_date, check_out_date, nights, "
                + "room_name, nightly_rate_cents, capacity_snapshot, total_cents, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, roomId, conversationId, contactId, guestName, guestPhone, guestsCount,
            Date.valueOf(checkIn), Date.valueOf(checkOut), nights, roomName, nightlyRateCents,
            capacitySnapshot, totalCents, notes);
        return findById(companyId, id).orElseThrow();
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update pousada_reservations set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de intervalo. */
    public static class DatesConflictException extends RuntimeException {
        private final transient PousadaReservationConflict conflict;

        public DatesConflictException(PousadaReservationConflict conflict) {
            this.conflict = conflict;
        }

        public PousadaReservationConflict conflict() {
            return conflict;
        }
    }
}
