package com.meada.profiles.comida.loyalty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code comida_loyalty_config} (onda 1 do comida, backlog #2 — clone do chassi sushi). 1:1 com
 * company. Ausente → {@link ComidaLoyaltyConfig#defaults}. {@link #upsert} grava (insert on conflict
 * update).
 */
@Repository
public class ComidaLoyaltyConfigRepository {

    private static final RowMapper<ComidaLoyaltyConfig> MAPPER = (rs, rn) -> new ComidaLoyaltyConfig(
        (UUID) rs.getObject("company_id"),
        rs.getBoolean("enabled"),
        rs.getInt("threshold_orders"),
        rs.getString("reward_kind"),
        rs.getInt("reward_value"));

    private final JdbcTemplate jdbcTemplate;

    public ComidaLoyaltyConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou os defaults (enabled=false) se não houver linha. */
    public ComidaLoyaltyConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select company_id, enabled, threshold_orders, reward_kind, reward_value "
                    + "from comida_loyalty_config where company_id = ?",
                MAPPER, companyId)
            .stream().findFirst().orElseGet(() -> ComidaLoyaltyConfig.defaults(companyId));
    }

    public ComidaLoyaltyConfig upsert(UUID companyId, boolean enabled, int thresholdOrders,
                                     String rewardKind, int rewardValue) {
        jdbcTemplate.update(
            "insert into comida_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
                + "values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set enabled = excluded.enabled, "
                + "threshold_orders = excluded.threshold_orders, reward_kind = excluded.reward_kind, "
                + "reward_value = excluded.reward_value, updated_at = now()",
            companyId, enabled, thresholdOrders, rewardKind, rewardValue);
        return findByCompany(companyId);
    }
}
