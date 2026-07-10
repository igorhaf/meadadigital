package com.meada.profiles.estetica.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/** Acesso a {@code aesthetic_config} (camada 8.3). 1:1 com company. Ausente → defaults. service_role. */
@Repository
public class AestheticConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AestheticConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AestheticConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, slot_minutes, reminder_enabled, auto_complete_enabled, "
                    + "auto_expire_enabled, package_validity_days, renewal_enabled, renewal_days, "
                    + "expiry_warning_days from aesthetic_config where company_id = ?",
                (rs, rn) -> new AestheticConfig(companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getInt("slot_minutes"),
                    rs.getBoolean("reminder_enabled"), rs.getBoolean("auto_complete_enabled"),
                    rs.getBoolean("auto_expire_enabled"),
                    rs.getObject("package_validity_days") == null ? null : rs.getInt("package_validity_days"),
                    rs.getBoolean("renewal_enabled"), rs.getInt("renewal_days"),
                    rs.getInt("expiry_warning_days")),
                companyId)
            .stream().findFirst().orElse(AestheticConfig.defaultFor(companyId));
    }

    public AestheticConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int slotMinutes,
                                  boolean reminderEnabled, boolean autoCompleteEnabled,
                                  boolean autoExpireEnabled, Integer packageValidityDays,
                                  boolean renewalEnabled, int renewalDays, int expiryWarningDays) {
        jdbcTemplate.update(
            "insert into aesthetic_config (company_id, opens_at, closes_at, slot_minutes, "
                + "reminder_enabled, auto_complete_enabled, auto_expire_enabled, package_validity_days, "
                + "renewal_enabled, renewal_days, expiry_warning_days) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, slot_minutes = excluded.slot_minutes, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "auto_expire_enabled = excluded.auto_expire_enabled, "
                + "package_validity_days = excluded.package_validity_days, "
                + "renewal_enabled = excluded.renewal_enabled, renewal_days = excluded.renewal_days, "
                + "expiry_warning_days = excluded.expiry_warning_days, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), slotMinutes, reminderEnabled,
            autoCompleteEnabled, autoExpireEnabled, packageValidityDays, renewalEnabled, renewalDays,
            expiryWarningDays);
        return findByCompany(companyId);
    }
}
