package com.meada.profiles.oficina.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code os_config} (camada 7.9). 1:1 com company. Horário INFORMATIVO (sem lógica de
 * slot). Ausente → defaults (08:00/18:00). service_role.
 */
@Repository
public class OficinaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public OficinaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OficinaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, return_reminder_enabled, return_reminder_days "
                    + "from os_config where company_id = ?",
                (rs, rn) -> new OficinaConfig(companyId, rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getBoolean("return_reminder_enabled"),
                    rs.getInt("return_reminder_days")),
                companyId)
            .stream().findFirst().orElse(OficinaConfig.defaultFor(companyId));
    }

    public OficinaConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt,
                                boolean returnReminderEnabled, int returnReminderDays) {
        jdbcTemplate.update(
            "insert into os_config (company_id, opens_at, closes_at, return_reminder_enabled, "
                + "return_reminder_days) values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, "
                + "return_reminder_enabled = excluded.return_reminder_enabled, "
                + "return_reminder_days = excluded.return_reminder_days, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt),
            returnReminderEnabled, returnReminderDays);
        return findByCompany(companyId);
    }
}
