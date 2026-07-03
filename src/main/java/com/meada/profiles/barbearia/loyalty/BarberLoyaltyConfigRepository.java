package com.meada.profiles.barbearia.loyalty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code barber_loyalty_config} (onda 1, backlog #3). 1:1 com company; ausência de linha =
 * desligada. service_role. Espelho do AdegaLoyaltyConfigRepository (sem reward kind/value — o prêmio
 * é sempre o corte GRÁTIS).
 */
@Repository
public class BarberLoyaltyConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public BarberLoyaltyConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BarberLoyaltyConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select enabled, threshold_cuts from barber_loyalty_config where company_id = ?",
                (rs, rn) -> new BarberLoyaltyConfig(companyId, rs.getBoolean("enabled"),
                    rs.getInt("threshold_cuts")),
                companyId)
            .stream().findFirst().orElse(BarberLoyaltyConfig.defaultFor(companyId));
    }

    public BarberLoyaltyConfig upsert(UUID companyId, boolean enabled, int thresholdCuts) {
        jdbcTemplate.update(
            "insert into barber_loyalty_config (company_id, enabled, threshold_cuts) values (?, ?, ?) "
                + "on conflict (company_id) do update set enabled = excluded.enabled, "
                + "threshold_cuts = excluded.threshold_cuts, updated_at = now()",
            companyId, enabled, thresholdCuts);
        return findByCompany(companyId);
    }
}
