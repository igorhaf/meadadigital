package com.meada.whatsapp.profiles.sushi;

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
                "select delivery_fee_cents, min_order_cents from sushi_restaurant_config "
                    + "where company_id = ?",
                (rs, rn) -> new SushiRestaurantConfig(
                    rs.getInt("delivery_fee_cents"), rs.getInt("min_order_cents")),
                companyId)
            .stream().findFirst().orElse(SushiRestaurantConfig.ZERO);
    }
}
