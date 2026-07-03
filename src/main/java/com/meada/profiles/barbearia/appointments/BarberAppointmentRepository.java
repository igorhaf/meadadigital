package com.meada.profiles.barbearia.appointments;

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
 * Acesso a {@code barber_appointments} (camada 8.1). Opera via service_role. Clone de
 * SalonAppointmentRepository: o conflito é por BARBEIRO (não company). {@link #findConflict} filtra
 * por {@code barber_id} — 2 clientes no mesmo horário com barbeiros DIFERENTES não conflitam. O
 * {@link #insertAppointment} re-verifica o conflito DENTRO da transação (defesa race) e materializa o
 * end_at + os snapshots.
 */
@Repository
public class BarberAppointmentRepository {

    private static final RowMapper<BarberAppointment> MAPPER = (rs, rn) -> new BarberAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("barber_id"),
        rs.getString("barber_name"),
        (UUID) rs.getObject("service_id"),
        rs.getString("service_name"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("guest_name"),
        rs.getString("guest_phone"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getInt("duration_minutes"),
        (Integer) rs.getObject("price_cents"),
        rs.getInt("discount_cents"),
        rs.getString("coupon_code_snapshot"),
        rs.getBoolean("loyalty_applied"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, barber_id, barber_name, service_id, service_name, conversation_id, contact_id, "
            + "guest_name, guest_phone, start_at, end_at, duration_minutes, price_cents, "
            + "discount_cents, coupon_code_snapshot, loyalty_applied, status, notes, "
            + "created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public BarberAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BarberAppointment> listByCompany(UUID companyId, String status, Instant dateFrom,
                                                 Instant dateTo, UUID barberId, UUID contactId,
                                                 int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from barber_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (barberId != null) { sql.append(" and barber_id = ?"); args.add(barberId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID barberId, UUID contactId) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from barber_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (barberId != null) { sql.append(" and barber_id = ?"); args.add(barberId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<BarberAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from barber_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Histórico do contato (mais recentes primeiro) — usado pela IA e pela tela. */
    public List<BarberAppointment> listByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from barber_appointments where company_id = ? and contact_id = ? "
                + "order by start_at desc limit ?",
            MAPPER, companyId, contactId, limit);
    }

    /** Agendamentos ATIVOS de um barbeiro na janela [from,to) — para slots livres da IA. */
    public List<BarberAppointment> listActiveByBarber(UUID companyId, UUID barberId,
                                                      Instant from, Instant to) {
        return jdbcTemplate.query(
            "select " + COLS + " from barber_appointments where company_id = ? and barber_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= ? and start_at < ? "
                + "order by start_at asc",
            MAPPER, companyId, barberId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por BARBEIRO: agendamento ATIVO (agendado/confirmado) do MESMO barbeiro cuja
     * janela sobrepõe [newStart, newEnd). Sobreposição = NOT (existing.end <= newStart OR
     * existing.start >= newEnd). Cálculo em SQL (defesa contra race na transação).
     */
    public Optional<BarberAppointmentConflict> findConflict(UUID barberId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, guest_name, start_at, end_at from barber_appointments "
                    + "where barber_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) "
                    + "order by start_at asc limit 1",
                (rs, rn) -> new BarberAppointmentConflict(
                    (UUID) rs.getObject("id"),
                    rs.getString("guest_name"),
                    rs.getTimestamp("start_at").toInstant(),
                    rs.getTimestamp("end_at").toInstant()),
                barberId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria o agendamento numa transação que RE-VERIFICA o conflito (por barbeiro) antes do insert.
     * end_at materializado; barber_name/service_name/price_cents/duration_minutes são snapshots.
     * Lança {@link SlotConflictException} se conflitar.
     */
    @Transactional
    public BarberAppointment insertAppointment(UUID companyId, UUID barberId, String barberName,
                                               UUID serviceId, String serviceName, Integer priceCents,
                                               int durationMinutes, UUID conversationId, UUID contactId,
                                               String guestName, String guestPhone, Instant startAt,
                                               String notes, int discountCents, UUID couponId,
                                               String couponCodeSnapshot, boolean loyaltyApplied) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<BarberAppointmentConflict> conflict = findConflict(barberId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into barber_appointments (company_id, barber_id, service_id, conversation_id, "
                + "contact_id, guest_name, guest_phone, start_at, duration_minutes, end_at, service_name, "
                + "barber_name, price_cents, notes, discount_cents, coupon_id, coupon_code_snapshot, "
                + "loyalty_applied) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, barberId, serviceId, conversationId, contactId, guestName,
            guestPhone, Timestamp.from(startAt), durationMinutes, Timestamp.from(endAt), serviceName,
            barberName, priceCents, notes, discountCents, couponId, couponCodeSnapshot, loyaltyApplied);
        return findById(companyId, id).orElseThrow();
    }

    /** Nº de agendamentos REALIZADOS do contato — alimenta a fidelidade #3 e o contexto da IA. */
    public int countRealizedByContact(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from barber_appointments where company_id = ? and contact_id = ? "
                + "and status = 'realizado'",
            Integer.class, companyId, contactId);
        return n == null ? 0 : n;
    }

    /** Agendamentos FUTUROS ativos do contato (para a IA capturar confirmação/cancelamento — #1). */
    public List<BarberAppointment> listUpcomingByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from barber_appointments where company_id = ? and contact_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= now() "
                + "order by start_at asc limit ?",
            MAPPER, companyId, contactId, limit);
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update barber_appointments set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient BarberAppointmentConflict conflict;

        public SlotConflictException(BarberAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public BarberAppointmentConflict conflict() {
            return conflict;
        }
    }
}
