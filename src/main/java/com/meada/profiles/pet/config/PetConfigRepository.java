package com.meada.profiles.pet.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/** Acesso a {@code pet_config} (camada 7.8). 1:1 com company. Ausente → defaults. service_role. */
@Repository
public class PetConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public PetConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PetConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, buffer_minutes, reminder_enabled from pet_config where company_id = ?",
                (rs, rn) -> new PetConfig(companyId, rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class), rs.getInt("buffer_minutes"),
                    rs.getBoolean("reminder_enabled")),
                companyId)
            .stream().findFirst().orElse(PetConfig.defaultFor(companyId));
    }

    public PetConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int bufferMinutes,
                            boolean reminderEnabled) {
        jdbcTemplate.update(
            "insert into pet_config (company_id, opens_at, closes_at, buffer_minutes, reminder_enabled) values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, buffer_minutes = excluded.buffer_minutes, "
                + "reminder_enabled = excluded.reminder_enabled, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), bufferMinutes, reminderEnabled);
        return findByCompany(companyId);
    }
}
