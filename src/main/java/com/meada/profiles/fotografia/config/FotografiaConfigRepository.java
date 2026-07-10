package com.meada.profiles.fotografia.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/** Acesso a {@code fotografia_config} (camada 8.16). 1:1 com company. Ausente → defaults. service_role. Espelho do DermatologiaConfigRepository (slot_minutes no lugar de buffer_minutes). */
@Repository
public class FotografiaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public FotografiaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FotografiaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, slot_minutes, reminder_enabled, auto_complete_enabled, "
                    + "auto_deliver_enabled, post_delivery_upsell_enabled, cancellation_policy_hours "
                    + "from fotografia_config where company_id = ?",
                (rs, rn) -> new FotografiaConfig(companyId, rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class), rs.getInt("slot_minutes"),
                    rs.getBoolean("reminder_enabled"), rs.getBoolean("auto_complete_enabled"),
                    rs.getBoolean("auto_deliver_enabled"), rs.getBoolean("post_delivery_upsell_enabled"),
                    rs.getObject("cancellation_policy_hours") == null
                        ? null : rs.getInt("cancellation_policy_hours")),
                companyId)
            .stream().findFirst().orElse(FotografiaConfig.defaultFor(companyId));
    }

    public FotografiaConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int slotMinutes,
                                   boolean reminderEnabled, boolean autoCompleteEnabled,
                                   boolean autoDeliverEnabled, boolean postDeliveryUpsellEnabled,
                                   Integer cancellationPolicyHours) {
        jdbcTemplate.update(
            "insert into fotografia_config (company_id, opens_at, closes_at, slot_minutes, "
                + "reminder_enabled, auto_complete_enabled, auto_deliver_enabled, "
                + "post_delivery_upsell_enabled, cancellation_policy_hours) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, slot_minutes = excluded.slot_minutes, "
                + "reminder_enabled = excluded.reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "auto_deliver_enabled = excluded.auto_deliver_enabled, "
                + "post_delivery_upsell_enabled = excluded.post_delivery_upsell_enabled, "
                + "cancellation_policy_hours = excluded.cancellation_policy_hours, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), slotMinutes, reminderEnabled,
            autoCompleteEnabled, autoDeliverEnabled, postDeliveryUpsellEnabled, cancellationPolicyHours);
        return findByCompany(companyId);
    }
}
