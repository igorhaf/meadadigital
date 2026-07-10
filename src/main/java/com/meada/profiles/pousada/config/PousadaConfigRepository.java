package com.meada.profiles.pousada.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code pousada_config} (camada 7.6). 1:1 com company. Ausente → defaults. service_role.
 */
@Repository
public class PousadaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public PousadaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PousadaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select check_in_time, check_out_time, cancellation_policy, reminder_enabled, "
                    + "auto_transition_enabled from pousada_config where company_id = ?",
                (rs, rn) -> new PousadaConfig(
                    companyId,
                    rs.getObject("check_in_time", LocalTime.class),
                    rs.getObject("check_out_time", LocalTime.class),
                    rs.getString("cancellation_policy"),
                    rs.getBoolean("reminder_enabled"),
                    rs.getBoolean("auto_transition_enabled")),
                companyId)
            .stream().findFirst().orElse(PousadaConfig.defaultFor(companyId));
    }

    public PousadaConfig upsert(UUID companyId, LocalTime checkInTime, LocalTime checkOutTime,
                               String cancellationPolicy, boolean reminderEnabled,
                               boolean autoTransitionEnabled) {
        jdbcTemplate.update(
            "insert into pousada_config (company_id, check_in_time, check_out_time, cancellation_policy, "
                + "reminder_enabled, auto_transition_enabled) "
                + "values (?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "check_in_time = excluded.check_in_time, check_out_time = excluded.check_out_time, "
                + "cancellation_policy = excluded.cancellation_policy, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_transition_enabled = excluded.auto_transition_enabled, updated_at = now()",
            companyId, Time.valueOf(checkInTime), Time.valueOf(checkOutTime), cancellationPolicy,
            reminderEnabled, autoTransitionEnabled);
        return findByCompany(companyId);
    }
}
