package com.meada.profiles.comida;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code comida_config} (camada 8.4). 1:1 com company. Ausente → {@link ComidaConfig#ZERO}.
 * Clone de {@link com.meada.profiles.sushi.SushiRestaurantConfigRepository} + upsert no
 * padrão do estetica (AestheticConfigRepository). Opera via service_role; o escopo por company_id
 * no WHERE é a defesa.
 */
@Repository
public class ComidaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public ComidaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou {@link ComidaConfig#ZERO} se não houver linha. */
    public ComidaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select delivery_fee_cents, min_order_cents, opens_at, closes_at, auto_deliver_hours, "
                    + "reactivation_enabled, reactivation_days, reactivation_coupon_code "
                    + "from comida_config where company_id = ?",
                (rs, rn) -> new ComidaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents"),
                    rs.getObject("opens_at", java.time.LocalTime.class),
                    rs.getObject("closes_at", java.time.LocalTime.class),
                    rs.getObject("auto_deliver_hours") == null ? null : rs.getInt("auto_deliver_hours"),
                    rs.getBoolean("reactivation_enabled"), rs.getInt("reactivation_days"),
                    rs.getString("reactivation_coupon_code")),
                companyId)
            .stream().findFirst().orElse(ComidaConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public ComidaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents,
                               java.time.LocalTime opensAt, java.time.LocalTime closesAt,
                               Integer autoDeliverHours, boolean reactivationEnabled,
                               int reactivationDays, String reactivationCouponCode) {
        jdbcTemplate.update(
            "insert into comida_config (company_id, delivery_fee_cents, min_order_cents, opens_at, "
                + "closes_at, auto_deliver_hours, reactivation_enabled, reactivation_days, "
                + "reactivation_coupon_code) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, "
                + "auto_deliver_hours = excluded.auto_deliver_hours, "
                + "reactivation_enabled = excluded.reactivation_enabled, "
                + "reactivation_days = excluded.reactivation_days, "
                + "reactivation_coupon_code = excluded.reactivation_coupon_code, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents,
            opensAt == null ? null : java.sql.Time.valueOf(opensAt),
            closesAt == null ? null : java.sql.Time.valueOf(closesAt),
            autoDeliverHours, reactivationEnabled, reactivationDays, reactivationCouponCode);
        return findByCompany(companyId);
    }
}
