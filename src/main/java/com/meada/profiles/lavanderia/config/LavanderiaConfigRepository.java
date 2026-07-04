package com.meada.profiles.lavanderia.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code lavanderia_config} (camada 8.10). 1:1 com company. Ausente → {@link
 * LavanderiaConfig#DEFAULT} (0/0/1). Clone de
 * {@link com.meada.profiles.floricultura.FloriculturaConfigRepository} + turnaround default.
 * Opera via service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class LavanderiaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public LavanderiaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link LavanderiaConfig#DEFAULT} se não houver linha. */
    public LavanderiaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, turnaround_days_default, "
                    + "express_enabled, express_surcharge_pct, express_turnaround_days, "
                    + "collect_reminder_enabled, ready_reminder_enabled, ready_reminder_days, "
                    + "reactivation_enabled, reactivation_days, reactivation_coupon_code "
                    + "from lavanderia_config where company_id = ?",
                (rs, rn) -> new LavanderiaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getInt("turnaround_days_default"),
                    rs.getBoolean("express_enabled"), rs.getInt("express_surcharge_pct"),
                    rs.getInt("express_turnaround_days"),
                    rs.getBoolean("collect_reminder_enabled"),
                    rs.getBoolean("ready_reminder_enabled"), rs.getInt("ready_reminder_days"),
                    rs.getBoolean("reactivation_enabled"), rs.getInt("reactivation_days"),
                    rs.getString("reactivation_coupon_code")),
                companyId)
            .stream().findFirst().orElse(LavanderiaConfig.DEFAULT);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public LavanderiaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                                   int turnaroundDaysDefault, boolean expressEnabled,
                                   int expressSurchargePct, int expressTurnaroundDays,
                                   boolean collectReminderEnabled, boolean readyReminderEnabled,
                                   int readyReminderDays, boolean reactivationEnabled,
                                   int reactivationDays, String reactivationCouponCode) {
        jdbcTemplate.update(
            "insert into lavanderia_config (company_id, delivery_fee_cents, min_order_cents, "
                + "turnaround_days_default, express_enabled, express_surcharge_pct, "
                + "express_turnaround_days, collect_reminder_enabled, ready_reminder_enabled, "
                + "ready_reminder_days, reactivation_enabled, reactivation_days, reactivation_coupon_code) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "turnaround_days_default = excluded.turnaround_days_default, "
                + "express_enabled = excluded.express_enabled, "
                + "express_surcharge_pct = excluded.express_surcharge_pct, "
                + "express_turnaround_days = excluded.express_turnaround_days, "
                + "collect_reminder_enabled = excluded.collect_reminder_enabled, "
                + "ready_reminder_enabled = excluded.ready_reminder_enabled, "
                + "ready_reminder_days = excluded.ready_reminder_days, "
                + "reactivation_enabled = excluded.reactivation_enabled, "
                + "reactivation_days = excluded.reactivation_days, "
                + "reactivation_coupon_code = excluded.reactivation_coupon_code, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents, turnaroundDaysDefault, expressEnabled,
            expressSurchargePct, expressTurnaroundDays, collectReminderEnabled, readyReminderEnabled,
            readyReminderDays, reactivationEnabled, reactivationDays, reactivationCouponCode);
        return findByCompany(companyId);
    }
}
