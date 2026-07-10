package com.meada.profiles.dermatologia.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/** Acesso a {@code dermatologia_config} (camada 8.11). 1:1 com company. Ausente → defaults. service_role. */
@Repository
public class DermatologiaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public DermatologiaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DermatologiaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, buffer_minutes, reminder_enabled, auto_complete_enabled, "
                    + "recall_enabled, recall_months from dermatologia_config where company_id = ?",
                (rs, rn) -> new DermatologiaConfig(companyId, rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class), rs.getInt("buffer_minutes"),
                    rs.getBoolean("reminder_enabled"), rs.getBoolean("auto_complete_enabled"),
                    rs.getBoolean("recall_enabled"), rs.getInt("recall_months")),
                companyId)
            .stream().findFirst().orElse(DermatologiaConfig.defaultFor(companyId));
    }

    public DermatologiaConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int bufferMinutes,
                                     boolean reminderEnabled, boolean autoCompleteEnabled,
                                     boolean recallEnabled, int recallMonths) {
        jdbcTemplate.update(
            "insert into dermatologia_config (company_id, opens_at, closes_at, buffer_minutes, "
                + "reminder_enabled, auto_complete_enabled, recall_enabled, recall_months) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, buffer_minutes = excluded.buffer_minutes, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "recall_enabled = excluded.recall_enabled, recall_months = excluded.recall_months, "
                + "updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), bufferMinutes, reminderEnabled,
            autoCompleteEnabled, recallEnabled, recallMonths);
        return findByCompany(companyId);
    }
}
