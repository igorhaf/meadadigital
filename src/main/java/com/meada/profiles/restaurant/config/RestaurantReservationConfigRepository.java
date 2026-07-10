package com.meada.profiles.restaurant.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code restaurant_reservation_config} (camada 7.3). 1:1 com company. Ausente → defaults
 * ({@link RestaurantReservationConfig#defaultFor}). Opera via service_role.
 */
@Repository
public class RestaurantReservationConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public RestaurantReservationConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou os defaults se não houver linha. */
    public RestaurantReservationConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select duration_minutes, buffer_minutes, opens_at, closes_at, reminder_enabled, "
                    + "auto_complete_enabled from restaurant_reservation_config where company_id = ?",
                (rs, rn) -> new RestaurantReservationConfig(
                    companyId,
                    rs.getInt("duration_minutes"),
                    rs.getInt("buffer_minutes"),
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getBoolean("reminder_enabled"),
                    rs.getBoolean("auto_complete_enabled")),
                companyId)
            .stream().findFirst().orElse(RestaurantReservationConfig.defaultFor(companyId));
    }

    /** Upsert (INSERT … ON CONFLICT) — cria ou atualiza a config 1:1 do tenant. */
    public RestaurantReservationConfig upsert(UUID companyId, int durationMinutes, int bufferMinutes,
                                              LocalTime opensAt, LocalTime closesAt,
                                              boolean reminderEnabled, boolean autoCompleteEnabled) {
        jdbcTemplate.update(
            "insert into restaurant_reservation_config "
                + "(company_id, duration_minutes, buffer_minutes, opens_at, closes_at, "
                + "reminder_enabled, auto_complete_enabled) "
                + "values (?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "duration_minutes = excluded.duration_minutes, "
                + "buffer_minutes = excluded.buffer_minutes, "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "updated_at = now()",
            companyId, durationMinutes, bufferMinutes, Time.valueOf(opensAt), Time.valueOf(closesAt),
            reminderEnabled, autoCompleteEnabled);
        return findByCompany(companyId);
    }
}
