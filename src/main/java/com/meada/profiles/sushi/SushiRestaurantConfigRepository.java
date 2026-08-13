package com.meada.profiles.sushi;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code sushi_restaurant_config} (camada 7.1). 1:1 com company. Ausente → ZERO.
 */
@Repository
public class SushiRestaurantConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public SushiRestaurantConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link SushiRestaurantConfig#ZERO} se não houver linha. */
    public SushiRestaurantConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, scheduling_enabled, upsell_enabled, "
                    + "reactivation_enabled, reactivation_days, reactivation_coupon_code "
                    + "from sushi_restaurant_config where company_id = ?",
                (rs, rn) -> new SushiRestaurantConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getBoolean("scheduling_enabled"), rs.getBoolean("upsell_enabled"),
                    rs.getBoolean("reactivation_enabled"), rs.getInt("reactivation_days"),
                    rs.getString("reactivation_coupon_code")),
                companyId)
            .stream().findFirst().orElse(SushiRestaurantConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public SushiRestaurantConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                                        boolean schedulingEnabled, boolean upsellEnabled,
                                        boolean reactivationEnabled, int reactivationDays,
                                        String reactivationCouponCode) {
        jdbcTemplate.update(
            "insert into sushi_restaurant_config "
                + "(company_id, delivery_fee_cents, min_order_cents, scheduling_enabled, "
                + "upsell_enabled, reactivation_enabled, reactivation_days, reactivation_coupon_code) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "scheduling_enabled = excluded.scheduling_enabled, "
                + "upsell_enabled = excluded.upsell_enabled, "
                + "reactivation_enabled = excluded.reactivation_enabled, "
                + "reactivation_days = excluded.reactivation_days, "
                + "reactivation_coupon_code = excluded.reactivation_coupon_code, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents, schedulingEnabled, upsellEnabled,
            reactivationEnabled, reactivationDays, reactivationCouponCode);
        return findByCompany(companyId);
    }
}
