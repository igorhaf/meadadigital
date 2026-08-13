package com.meada.profiles.sushi.loyalty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code sushi_loyalty_config} (camada 7.1 / sushi funcional). 1:1 com company. Ausente →
 * {@link SushiLoyaltyConfig#defaults}. {@link #upsert} grava (insert on conflict update).
 */
@Repository
public class SushiLoyaltyConfigRepository {

    private static final RowMapper<SushiLoyaltyConfig> MAPPER = (rs, rn) -> new SushiLoyaltyConfig(
        (UUID) rs.getObject("company_id"),
        rs.getBoolean("enabled"),
        rs.getInt("threshold_orders"),
        rs.getString("reward_kind"),
        rs.getInt("reward_value"));

    private final JdbcTemplate jdbcTemplate;

    public SushiLoyaltyConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Config do tenant, ou os defaults (enabled=false) se não houver linha. */
    public SushiLoyaltyConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select company_id, enabled, threshold_orders, reward_kind, reward_value "
                    + "from sushi_loyalty_config where company_id = ?",
                MAPPER, companyId)
            .stream().findFirst().orElseGet(() -> SushiLoyaltyConfig.defaults(companyId));
    }

    public SushiLoyaltyConfig upsert(UUID companyId, boolean enabled, int thresholdOrders,
                                     String rewardKind, int rewardValue) {
        jdbcTemplate.update(
            "insert into sushi_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
                + "values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set enabled = excluded.enabled, "
                + "threshold_orders = excluded.threshold_orders, reward_kind = excluded.reward_kind, "
                + "reward_value = excluded.reward_value, updated_at = now()",
            companyId, enabled, thresholdOrders, rewardKind, rewardValue);
        return findByCompany(companyId);
    }
}
