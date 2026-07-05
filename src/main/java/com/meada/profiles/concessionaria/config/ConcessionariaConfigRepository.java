package com.meada.profiles.concessionaria.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code concessionaria_config} (camada 8.17). 1:1 com company. Ausente → defaults
 * ({@link ConcessionariaConfig#defaultFor}). Opera via service_role.
 */
@Repository
public class ConcessionariaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConcessionariaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ConcessionariaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, duration_minutes, buffer_minutes, opens_at, closes_at, notes, "
                    + "followup_enabled, followup_days, testdrive_reminder_enabled, auto_complete_enabled, "
                    + "post_sale_enabled, review_link, service_reminder_enabled, service_reminder_months "
                    + "from concessionaria_config where company_id = ?",
                (rs, rn) -> new ConcessionariaConfig(
                    companyId,
                    rs.getString("business_name"),
                    rs.getInt("duration_minutes"),
                    rs.getInt("buffer_minutes"),
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getString("notes"),
                    rs.getBoolean("followup_enabled"),
                    rs.getInt("followup_days"),
                    rs.getBoolean("testdrive_reminder_enabled"),
                    rs.getBoolean("auto_complete_enabled"),
                    rs.getBoolean("post_sale_enabled"),
                    rs.getString("review_link"),
                    rs.getBoolean("service_reminder_enabled"),
                    rs.getInt("service_reminder_months")),
                companyId)
            .stream().findFirst().orElse(ConcessionariaConfig.defaultFor(companyId));
    }

    /** Upsert (INSERT … ON CONFLICT) — cria ou atualiza a config 1:1 do tenant. */
    public ConcessionariaConfig upsert(UUID companyId, String businessName, int durationMinutes,
                                       int bufferMinutes, LocalTime opensAt, LocalTime closesAt,
                                       String notes, boolean followupEnabled, int followupDays,
                                       boolean testdriveReminderEnabled, boolean autoCompleteEnabled,
                                       boolean postSaleEnabled, String reviewLink,
                                       boolean serviceReminderEnabled, int serviceReminderMonths) {
        jdbcTemplate.update(
            "insert into concessionaria_config "
                + "(company_id, business_name, duration_minutes, buffer_minutes, opens_at, closes_at, notes, "
                + "followup_enabled, followup_days, testdrive_reminder_enabled, auto_complete_enabled, "
                + "post_sale_enabled, review_link, service_reminder_enabled, service_reminder_months) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "business_name = excluded.business_name, "
                + "duration_minutes = excluded.duration_minutes, "
                + "buffer_minutes = excluded.buffer_minutes, "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "notes = excluded.notes, "
                + "followup_enabled = excluded.followup_enabled, "
                + "followup_days = excluded.followup_days, "
                + "testdrive_reminder_enabled = excluded.testdrive_reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "post_sale_enabled = excluded.post_sale_enabled, "
                + "review_link = excluded.review_link, "
                + "service_reminder_enabled = excluded.service_reminder_enabled, "
                + "service_reminder_months = excluded.service_reminder_months, "
                + "updated_at = now()",
            companyId, businessName, durationMinutes, bufferMinutes,
            Time.valueOf(opensAt), Time.valueOf(closesAt), notes,
            followupEnabled, followupDays, testdriveReminderEnabled, autoCompleteEnabled,
            postSaleEnabled, reviewLink, serviceReminderEnabled, serviceReminderMonths);
        return findByCompany(companyId);
    }
}
