package com.meada.whatsapp.profiles.estetica.notes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code aesthetic_session_notes} (camada 8.3). 1:1 com o agendamento. service_role. */
@Repository
public class AestheticSessionNoteRepository {

    private static final RowMapper<AestheticSessionNote> MAPPER = (rs, rn) -> new AestheticSessionNote(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("appointment_id"),
        rs.getString("treated_area"),
        rs.getString("device_params"),
        rs.getString("observations"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, appointment_id, treated_area, device_params, observations, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AestheticSessionNoteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AestheticSessionNote> findByAppointment(UUID companyId, UUID appointmentId) {
        return jdbcTemplate.query(
                "select " + COLS + " from aesthetic_session_notes where company_id = ? and appointment_id = ?",
                MAPPER, companyId, appointmentId)
            .stream().findFirst();
    }

    /** Upsert por appointment_id (1:1). */
    public AestheticSessionNote upsert(UUID companyId, UUID appointmentId, String treatedArea,
                                       String deviceParams, String observations) {
        jdbcTemplate.update(
            "insert into aesthetic_session_notes (company_id, appointment_id, treated_area, device_params, observations) "
                + "values (?, ?, ?, ?, ?) "
                + "on conflict (appointment_id) do update set treated_area = excluded.treated_area, "
                + "device_params = excluded.device_params, observations = excluded.observations, updated_at = now()",
            companyId, appointmentId, treatedArea, deviceParams, observations);
        return findByAppointment(companyId, appointmentId).orElseThrow();
    }
}
