package com.meada.profiles.barbearia.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code barber_config} (camada 8.1). 1:1 com company. Ausente → defaults. service_role.
 * Espelho de SalonConfigRepository + slot_minutes e queue_enabled.
 */
@Repository
public class BarberConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public BarberConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BarberConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, slot_minutes, queue_enabled, reminder_enabled, "
                    + "auto_complete_enabled, upsell_enabled from barber_config where company_id = ?",
                (rs, rn) -> new BarberConfig(
                    companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getInt("slot_minutes"),
                    rs.getBoolean("queue_enabled"),
                    rs.getBoolean("reminder_enabled"),
                    rs.getBoolean("auto_complete_enabled"),
                    rs.getBoolean("upsell_enabled")),
                companyId)
            .stream().findFirst().orElse(BarberConfig.defaultFor(companyId));
    }

    public BarberConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int slotMinutes,
                               boolean queueEnabled, boolean reminderEnabled, boolean autoCompleteEnabled,
                               boolean upsellEnabled) {
        jdbcTemplate.update(
            "insert into barber_config (company_id, opens_at, closes_at, slot_minutes, queue_enabled, "
                + "reminder_enabled, auto_complete_enabled, upsell_enabled) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "slot_minutes = excluded.slot_minutes, queue_enabled = excluded.queue_enabled, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "upsell_enabled = excluded.upsell_enabled, "
                + "updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), slotMinutes, queueEnabled,
            reminderEnabled, autoCompleteEnabled, upsellEnabled);
        return findByCompany(companyId);
    }
}
