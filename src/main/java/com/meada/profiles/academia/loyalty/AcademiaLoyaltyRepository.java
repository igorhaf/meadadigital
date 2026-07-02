package com.meada.profiles.academia.loyalty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Acesso a {@code academia_loyalty_config} + {@code academia_loyalty} (camada 7.7, feature #12).
 * Opera via service_role; escopo por company_id. A config é 1:1 com a company (upsert por
 * on conflict); o saldo é upsert por (company_id, contact_id) somando os pontos atomicamente no
 * próprio UPDATE (points = points + ?), evitando corrida de leitura-modificação-escrita.
 */
@Repository
public class AcademiaLoyaltyRepository {

    private static final RowMapper<AcademiaLoyaltyConfig> CONFIG_MAPPER = (rs, rn) -> new AcademiaLoyaltyConfig(
        (UUID) rs.getObject("company_id"),
        rs.getBoolean("enabled"),
        rs.getInt("points_per_checkin"),
        (Integer) rs.getObject("reward_threshold"),
        rs.getString("reward_text"));

    private final JdbcTemplate jdbcTemplate;

    public AcademiaLoyaltyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- config -------------------------------------------------------------

    /** Config do tenant; ausente → defaults. */
    public AcademiaLoyaltyConfig findConfig(UUID companyId) {
        return jdbcTemplate.query(
                "select company_id, enabled, points_per_checkin, reward_threshold, reward_text "
                    + "from academia_loyalty_config where company_id = ?",
                CONFIG_MAPPER, companyId)
            .stream().findFirst().orElse(AcademiaLoyaltyConfig.defaultFor(companyId));
    }

    public AcademiaLoyaltyConfig upsertConfig(UUID companyId, boolean enabled, int pointsPerCheckin,
                                              Integer rewardThreshold, String rewardText) {
        jdbcTemplate.update(
            "insert into academia_loyalty_config "
                + "(company_id, enabled, points_per_checkin, reward_threshold, reward_text) "
                + "values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "enabled = excluded.enabled, points_per_checkin = excluded.points_per_checkin, "
                + "reward_threshold = excluded.reward_threshold, reward_text = excluded.reward_text, "
                + "updated_at = now()",
            companyId, enabled, pointsPerCheckin, rewardThreshold, rewardText);
        return findConfig(companyId);
    }

    // ---- saldo --------------------------------------------------------------

    /** True se o contato existe e é do tenant. */
    public boolean contactExists(UUID companyId, UUID contactId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from contacts where company_id = ? and id = ?",
            Integer.class, companyId, contactId);
        return n != null && n > 0;
    }

    /** Saldo do contato; ausente → 0. */
    public AcademiaLoyaltyBalance findBalance(UUID companyId, UUID contactId) {
        return jdbcTemplate.query(
                "select company_id, contact_id, points, updated_at from academia_loyalty "
                    + "where company_id = ? and contact_id = ?",
                (rs, rn) -> {
                    Timestamp updated = rs.getTimestamp("updated_at");
                    return new AcademiaLoyaltyBalance(
                        (UUID) rs.getObject("company_id"),
                        (UUID) rs.getObject("contact_id"),
                        rs.getInt("points"),
                        updated == null ? null : updated.toInstant());
                },
                companyId, contactId)
            .stream().findFirst()
            .orElse(AcademiaLoyaltyBalance.zeroFor(companyId, contactId));
    }

    /** Credita {@code points} ao saldo do contato de forma atômica (upsert somando). Retorna o novo saldo. */
    public AcademiaLoyaltyBalance addPoints(UUID companyId, UUID contactId, int points) {
        jdbcTemplate.update(
            "insert into academia_loyalty (company_id, contact_id, points) values (?, ?, ?) "
                + "on conflict (company_id, contact_id) do update set "
                + "points = academia_loyalty.points + excluded.points, updated_at = now()",
            companyId, contactId, points);
        return findBalance(companyId, contactId);
    }
}
