package com.meada.profiles.las;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code las_config} (camada 8.23). 1:1 com company. Ausente → {@link LasConfig#ZERO}.
 * Clone de {@link com.meada.profiles.lingerie.LingerieConfigRepository} (chassi de varejo).
 * Opera via service_role; o escopo por company_id no WHERE é a defesa.
 */
@Repository
public class LasConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public LasConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link LasConfig#ZERO} se não houver linha. */
    public LasConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, reactivation_enabled, reactivation_days, "
                    + "reactivation_coupon_code from las_config where company_id = ?",
                (rs, rn) -> new LasConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getBoolean("reactivation_enabled"), rs.getInt("reactivation_days"),
                    rs.getString("reactivation_coupon_code")),
                companyId)
            .stream().findFirst().orElse(LasConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public LasConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                            boolean reactivationEnabled, int reactivationDays,
                            String reactivationCouponCode) {
        jdbcTemplate.update(
            "insert into las_config (company_id, delivery_fee_cents, min_order_cents, "
                + "reactivation_enabled, reactivation_days, reactivation_coupon_code) "
                + "values (?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "reactivation_enabled = excluded.reactivation_enabled, "
                + "reactivation_days = excluded.reactivation_days, "
                + "reactivation_coupon_code = excluded.reactivation_coupon_code, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents, reactivationEnabled, reactivationDays,
            reactivationCouponCode);
        return findByCompany(companyId);
    }
}
