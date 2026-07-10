package com.meada.profiles.nutri.appointments;

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
 * Acesso a {@code nutri_appointments} (camada 8.0). Opera via service_role. Conflito por PROFISSIONAL
 * (half-open, espelho pet/salon): {@link #insertAppointment} re-verifica na transação (defesa race),
 * materializa end_at e os snapshots.
 */
@Repository
public class NutriAppointmentRepository {

    private static final RowMapper<NutriAppointment> MAPPER = (rs, rn) -> new NutriAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("patient_id"),
        rs.getString("patient_name"),
        rs.getString("patient_phone"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getString("appointment_type"),
        rs.getInt("duration_minutes"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, patient_id, patient_name, patient_phone, contact_id, "
            + "conversation_id, appointment_type, duration_minutes, start_at, end_at, status, notes, "
            + "created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public NutriAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<NutriAppointment> listByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                                UUID professionalId, UUID patientId, UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from nutri_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (patientId != null) { sql.append(" and patient_id = ?"); args.add(patientId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_at asc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                               UUID professionalId, UUID patientId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from nutri_appointments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (dateFrom != null) { sql.append(" and start_at >= ?"); args.add(Timestamp.from(dateFrom)); }
        if (dateTo != null) { sql.append(" and start_at < ?"); args.add(Timestamp.from(dateTo)); }
        if (professionalId != null) { sql.append(" and professional_id = ?"); args.add(professionalId); }
        if (patientId != null) { sql.append(" and patient_id = ?"); args.add(patientId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<NutriAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from nutri_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public List<NutriAppointment> listByPatient(UUID companyId, UUID patientId, int limit) {
        return jdbcTemplate.query("select " + COLS + " from nutri_appointments where company_id = ? and patient_id = ? "
                + "order by start_at desc limit ?", MAPPER, companyId, patientId, limit);
    }

    public List<NutriAppointment> listActiveByProfessional(UUID companyId, UUID professionalId, Instant from, Instant to) {
        return jdbcTemplate.query("select " + COLS + " from nutri_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por PROFISSIONAL: consulta ATIVA (agendado/confirmado) do MESMO profissional
     * cuja janela sobrepõe [newStart, newEnd). Half-open. Cálculo em SQL (defesa contra race).
     */
    public Optional<NutriAppointmentConflict> findConflict(UUID professionalId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, patient_name, start_at, end_at from nutri_appointments "
                    + "where professional_id = ? and status in ('agendado','confirmado') "
                    + "and not (end_at <= ? or start_at >= ?) order by start_at asc limit 1",
                (rs, rn) -> new NutriAppointmentConflict(
                    (UUID) rs.getObject("id"), rs.getString("patient_name"),
                    rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant()),
                professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria a consulta numa transação que RE-VERIFICA o conflito (por profissional) antes do insert.
     * end_at materializado; snapshots de paciente/profissional. Lança {@link SlotConflictException}.
     */
    @Transactional
    public NutriAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                              UUID patientId, String patientName, String patientPhone,
                                              UUID contactId, UUID conversationId, String appointmentType,
                                              int durationMinutes, Instant startAt, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<NutriAppointmentConflict> conflict = findConflict(professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into nutri_appointments (company_id, professional_id, patient_id, contact_id, conversation_id, "
                + "patient_name, patient_phone, professional_name, appointment_type, duration_minutes, start_at, end_at, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, patientId, contactId, conversationId, patientName, patientPhone,
            professionalName, appointmentType, durationMinutes, Timestamp.from(startAt), Timestamp.from(endAt), notes);
        return findById(companyId, id).orElseThrow();
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update("update nutri_appointments set status = ?, status_updated_at = now() "
            + "where company_id = ? and id = ?", newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient NutriAppointmentConflict conflict;

        public SlotConflictException(NutriAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public NutriAppointmentConflict conflict() {
            return conflict;
        }
    }

    /** Próximas consultas ATIVAS do CONTATO — bloco do contexto (onda 1, tag de confirmação). */
    public List<NutriAppointment> listUpcomingByContact(UUID companyId, UUID contactId, int limit) {
        return jdbcTemplate.query(
            "select " + COLS + " from nutri_appointments where company_id = ? and contact_id = ? "
                + "and status in ('agendado','confirmado') and start_at >= now() "
                + "order by start_at asc limit " + Math.max(1, limit),
            MAPPER, companyId, contactId);
    }
}
