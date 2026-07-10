package com.meada.profiles.pizzaria.loyalty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code pizzaria_loyalty_config} (camada 8.9, backlog #2 — clone do chassi sushi). 1:1 com
 * company. Ausente → {@link PizzariaLoyaltyConfig#defaults}. {@link #upsert} grava (insert on conflict
 * update).
 */
@Repository
public class PizzariaLoyaltyConfigRepository {

    private static final RowMapper<PizzariaLoyaltyConfig> MAPPER = (rs, rn) -> new PizzariaLoyaltyConfig(
        (UUID) rs.getObject("company_id"),
        rs.getBoolean("enabled"),
        rs.getInt("threshold_orders"),
        rs.getString("reward_kind"),
        rs.getInt("reward_value"));

    private final JdbcTemplate jdbcTemplate;

    public PizzariaLoyaltyConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou os defaults (enabled=false) se não houver linha. */
    public PizzariaLoyaltyConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select company_id, enabled, threshold_orders, reward_kind, reward_value "
                    + "from pizzaria_loyalty_config where company_id = ?",
                MAPPER, companyId)
            .stream().findFirst().orElseGet(() -> PizzariaLoyaltyConfig.defaults(companyId));
    }

    public PizzariaLoyaltyConfig upsert(UUID companyId, boolean enabled, int thresholdOrders,
                                     String rewardKind, int rewardValue) {
        jdbcTemplate.update(
            "insert into pizzaria_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
                + "values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set enabled = excluded.enabled, "
                + "threshold_orders = excluded.threshold_orders, reward_kind = excluded.reward_kind, "
                + "reward_value = excluded.reward_value, updated_at = now()",
            companyId, enabled, thresholdOrders, rewardKind, rewardValue);
        return findByCompany(companyId);
    }
}
