package com.meada.whatsapp.profiles.dermatologia.appointments;

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
 * Acesso a {@code dermatologia_appointments} (camada 8.11). Opera via service_role. Conflito por
 * PROFISSIONAL (half-open, espelho nutri/salon): {@link #insertAppointment} re-verifica na transação
 * (defesa race), materializa end_at e os snapshots.
 */
@Repository
public class DermatologiaAppointmentRepository {

    private static final RowMapper<DermatologiaAppointment> MAPPER = (rs, rn) -> new DermatologiaAppointment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("professional_id"),
        rs.getString("professional_name"),
        (UUID) rs.getObject("patient_id"),
        rs.getString("patient_name"),
        rs.getString("patient_phone"),
        (UUID) rs.getObject("procedure_type_id"),
        rs.getString("procedure_type_name"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("conversation_id"),
        rs.getInt("duration_minutes"),
        rs.getTimestamp("start_at").toInstant(),
        rs.getTimestamp("end_at").toInstant(),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, professional_id, professional_name, patient_id, patient_name, patient_phone, procedure_type_id, "
            + "procedure_type_name, contact_id, conversation_id, duration_minutes, start_at, end_at, status, notes, "
            + "created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public DermatologiaAppointmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DermatologiaAppointment> listByCompany(UUID companyId, String status, Instant dateFrom, Instant dateTo,
                                                       UUID professionalId, UUID patientId, UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from dermatologia_appointments where company_id = ?");
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
        StringBuilder sql = new StringBuilder("select count(*) from dermatologia_appointments where company_id = ?");
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

    public Optional<DermatologiaAppointment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from dermatologia_appointments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public List<DermatologiaAppointment> listByPatient(UUID companyId, UUID patientId, int limit) {
        return jdbcTemplate.query("select " + COLS + " from dermatologia_appointments where company_id = ? and patient_id = ? "
                + "order by start_at desc limit ?", MAPPER, companyId, patientId, limit);
    }

    public List<DermatologiaAppointment> listActiveByProfessional(UUID companyId, UUID professionalId, Instant from, Instant to) {
        return jdbcTemplate.query("select " + COLS + " from dermatologia_appointments where company_id = ? and professional_id = ? "
                + "and status in ('agendada','confirmada') and start_at >= ? and start_at < ? order by start_at asc",
            MAPPER, companyId, professionalId, Timestamp.from(from), Timestamp.from(to));
    }

    /**
     * Conflito de slot por PROFISSIONAL: consulta ATIVA (agendada/confirmada) do MESMO profissional
     * cuja janela sobrepõe [newStart, newEnd). Half-open. Cálculo em SQL (defesa contra race).
     */
    public Optional<DermatologiaAppointmentConflict> findConflict(UUID professionalId, Instant newStart, Instant newEnd) {
        return jdbcTemplate.query(
                "select id, patient_name, start_at, end_at from dermatologia_appointments "
                    + "where professional_id = ? and status in ('agendada','confirmada') "
                    + "and not (end_at <= ? or start_at >= ?) order by start_at asc limit 1",
                (rs, rn) -> new DermatologiaAppointmentConflict(
                    (UUID) rs.getObject("id"), rs.getString("patient_name"),
                    rs.getTimestamp("start_at").toInstant(), rs.getTimestamp("end_at").toInstant()),
                professionalId, Timestamp.from(newStart), Timestamp.from(newEnd))
            .stream().findFirst();
    }

    /**
     * Cria a consulta numa transação que RE-VERIFICA o conflito (por profissional) antes do insert.
     * end_at materializado; snapshots de paciente/profissional/tipo. Lança {@link SlotConflictException}.
     */
    @Transactional
    public DermatologiaAppointment insertAppointment(UUID companyId, UUID professionalId, String professionalName,
                                                     UUID patientId, String patientName, String patientPhone,
                                                     UUID procedureTypeId, String procedureTypeName,
                                                     UUID contactId, UUID conversationId,
                                                     int durationMinutes, Instant startAt, String notes) {
        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        Optional<DermatologiaAppointmentConflict> conflict = findConflict(professionalId, startAt, endAt);
        if (conflict.isPresent()) {
            throw new SlotConflictException(conflict.get());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into dermatologia_appointments (company_id, professional_id, patient_id, procedure_type_id, "
                + "contact_id, conversation_id, patient_name, patient_phone, professional_name, procedure_type_name, "
                + "duration_minutes, start_at, end_at, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, professionalId, patientId, procedureTypeId, contactId, conversationId,
            patientName, patientPhone, professionalName, procedureTypeName, durationMinutes,
            Timestamp.from(startAt), Timestamp.from(endAt), notes);
        return findById(companyId, id).orElseThrow();
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update("update dermatologia_appointments set status = ?, status_updated_at = now() "
            + "where company_id = ? and id = ?", newStatus, companyId, id);
    }

    /** Lançada pelo insert quando o re-check transacional detecta conflito de slot. */
    public static class SlotConflictException extends RuntimeException {
        private final transient DermatologiaAppointmentConflict conflict;

        public SlotConflictException(DermatologiaAppointmentConflict conflict) {
            this.conflict = conflict;
        }

        public DermatologiaAppointmentConflict conflict() {
            return conflict;
        }
    }
}
