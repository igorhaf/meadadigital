package com.meada.whatsapp.profiles.comida;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code comida_config} (camada 8.4). 1:1 com company. Ausente → {@link ComidaConfig#ZERO}.
 * Clone de {@link com.meada.whatsapp.profiles.sushi.SushiRestaurantConfigRepository} + upsert no
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
                "select delivery_fee_cents, min_order_cents from comida_config where company_id = ?",
                (rs, rn) -> new ComidaConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(ComidaConfig.ZERO);
    }

    /** Upsert da config (insert ou update por company_id). Mantém updated_at. */
    public ComidaConfig upsert(UUID companyId, int deliveryFeeCents, int minOrderCents) {
        jdbcTemplate.update(
            "insert into comida_config (company_id, delivery_fee_cents, min_order_cents) "
                + "values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "delivery_fee_cents = excluded.delivery_fee_cents, "
                + "min_order_cents = excluded.min_order_cents, updated_at = now()",
            companyId, deliveryFeeCents, minOrderCents);
        return findByCompany(companyId);
    }
}
