package com.meada.profiles.restaurant.reservations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code table_reservations} (camada 7.3). Opera via service_role; o escopo por company_id
 * no WHERE é a defesa. A criação re-verifica conflito DENTRO da transação (decisão 5) — defesa
 * contra race: a IA pode ter visto disponibilidade no cache (15s) e, no instante de persistir,
 * outra reserva já ter ocupado o slot. O conflito é decidido em SQL (janela materializada), não em
 * Java.
 */
@Repository
public class ReservationRepository {

    private static final RowMapper<Reservation> MAPPER = (rs, rn) -> new Reservation(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("table_id"),
        rs.getString("table_label"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getInt("duration_minutes"),
        rs.getInt("num_people"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String SELECT =
        "select r.id, r.table_id, t.label as table_label, r.conversation_id, r.contact_id, "
            + "r.guest_name, r.guest_phone, r.start_at, r.end_at, r.duration_minutes, r.num_people, "
            + "r.status, r.notes, r.created_at, r.status_updated_at "
            + "from table_reservations r join restaurant_tables t on t.id = r.table_id ";

    private final JdbcTemplate jdbcTemplate;

    public ReservationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Lista reservas do tenant com filtros opcionais (status, janela [from,to)), paginado, por
     * start_at ascendente (agenda). dateFrom/dateTo em Instant (limites da janela).
     */
    public List<Reservation> listByCompany(UUID companyId, String status, Instant dateFrom,
                                           Instant dateTo, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT + "where r.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and r.status = ?");
            args.add(status);
        }
        if (dateFrom != null) {
            sql.append(" and r.start_at >= ?");
            args.add(Timestamp.from(dateFrom));
        }
        if (dateTo != null) {
            sql.append(" and r.start_at < ?");
            args.add(Timestamp.from(dateTo));
        }
        sql.append(" order by r.start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from table_reservations where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and status = ?");
            args.add(status);
        }
        if (dateFrom != null) {
            sql.append(" and start_at >= ?");
            args.add(Timestamp.from(dateFrom));
        }
        if (dateTo != null) {
            sql.append(" and start_at < ?");
            args.add(Timestamp.from(dateTo));
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<Reservation> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(SELECT + "where r.company_id = ? and r.id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Reservas ATIVAS (pendente/confirmada) de uma mesa, na janela [from,to) — para o contexto IA. */
    public List<Reservation> listActiveUpcoming(UUID companyId, Instant from, Instant to) {
        return jdbcTemplate.query(
            SELECT + "where r.company_id = ? and r.status in ('pendente','confirmada') "
                + "and r.start_at >= ? and r.start_at < ? order by r.start_at asc",
            MAPPER, companyId, Timestamp.from(from), Timestamp.from(to));
    }

    /** Próximas reservas ATIVAS do CONTATO — bloco fresco do contexto (onda 1, tag de confirmação). */
    public List<Reservation> listUpcomingByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            SELECT + "where r.company_id = ? and r.contact_id = ? "
                + "and r.status in ('pendente','confirmada') and r.start_at >= now() "
                + "order by r.start_at asc limit " + Math.max(1, limit),
            MAPPER, companyId, contactId);
    }

    /**
     * Conflito de slot (decisão 5): reserva ATIVA (pendente/confirmada) na MESMA mesa cuja janela
     * sobrepõe [newStart, newEnd). Sobreposição = NOT (existing.end <= newStart OR existing.start
     * >= newEnd). O cálculo é no SQL (defesa contra race quando chamado dentro da transação de
     * insert). Devolve o primeiro conflito (LIMIT 1) ou empty.
     */
    public Optional<ReservationConflict> findConflict(UUID tableId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, guest_name, start_at, end_at from table_reservations "
                    + "where table_id = ? and status in ('pendente','confirmada') "
                    + "and not (end_at <= ? or start_at >= ?) "
                    + "order by start_at asc limit 1",
                (rs, rn) -> new ReservationConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("guest_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                tableId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria a reserva numa transação que RE-VERIFICA o conflito imediatamente antes do insert
     * (decisão 5: fecha a janela de race). end_at é materializado (startAt + durationMinutes).
     * Lança {@link SlotConflictException} se houver conflito — o service a mapeia.
     */
    @Transactional
    public Reservation insertReservation(UUID companyId, UUID tableId, UUID conversationId,
                                         UUID contactId, String guestName, String guestPhone,
                                         Instant startAt, int durationMinutes, int numPeople,
                                         String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        // Re-check transacional: se outra reserva ocupou o slot entre o cache da IA e agora, aborta.
        Optional<ReservationConflict> conflict = findConflict(tableId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into table_reservations (company_id, table_id, conversation_id, contact_id, "
                + "guest_name, guest_phone, start_at, duration_minutes, end_at, num_people, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, tableId, conversationId, contactId, guestName, guestPhone,
            Timestamp.from(startAt), durationMinutes, Timestamp.from(endAt), numPeople, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição de status + status_updated_at. Service já validou a transição. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update table_reservations set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient ReservationConflict conflict;

        public SlotConflictException(ReservationConflict conflict) {
            this.conflict = conflict;
        }

        public ReservationConflict conflict() {
            return conflict;
        }
    }
}
