package com.meada.profiles.dental.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code dental_clinic_config} (camada 7.4). 1:1 com company. Ausente → defaults
 * ({@link DentalClinicConfig#defaultFor}). Opera via service_role.
 */
@Repository
public class DentalClinicConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public DentalClinicConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DentalClinicConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select duration_minutes, buffer_minutes, opens_at, closes_at, reminder_enabled, "
                    + "auto_complete_enabled, recall_enabled, recall_months "
                    + "from dental_clinic_config where company_id = ?",
                (rs, rn) -> new DentalClinicConfig(
                    companyId,
                    rs.getInt("duration_minutes"),
                    rs.getInt("buffer_minutes"),
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getBoolean("reminder_enabled"),
                    rs.getBoolean("auto_complete_enabled"),
                    rs.getBoolean("recall_enabled"),
                    rs.getInt("recall_months")),
                companyId)
            .stream().findFirst().orElse(DentalClinicConfig.defaultFor(companyId));
    }

    /** Upsert (INSERT … ON CONFLICT) — cria ou atualiza a config 1:1 do tenant. */
    public DentalClinicConfig upsert(UUID companyId, int durationMinutes, int bufferMinutes,
                                     LocalTime opensAt, LocalTime closesAt, boolean reminderEnabled,
                                     boolean autoCompleteEnabled, boolean recallEnabled, int recallMonths) {
        jdbcTemplate.update(
            "insert into dental_clinic_config "
                + "(company_id, duration_minutes, buffer_minutes, opens_at, closes_at, "
                + "reminder_enabled, auto_complete_enabled, recall_enabled, recall_months) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "duration_minutes = excluded.duration_minutes, "
                + "buffer_minutes = excluded.buffer_minutes, "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "recall_enabled = excluded.recall_enabled, "
                + "recall_months = excluded.recall_months, "
                + "updated_at = now()",
            companyId, durationMinutes, bufferMinutes, Time.valueOf(opensAt), Time.valueOf(closesAt),
            reminderEnabled, autoCompleteEnabled, recallEnabled, recallMonths);
        return findByCompany(companyId);
    }
}
