package com.meada.whatsapp.profiles.features;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Acesso a {@code profile_features} (camada 9.0). Tabela de PLATAFORMA — opera via service_role
 * (o root mexe fora do RLS). Guarda só os DESVIOS do default; ausência de linha = OFF (o resolver
 * no {@link ProfileFeatureService} trata como false).
 */
@Repository
public class ProfileFeatureRepository {

    /** Uma linha de flag (desvio do default). */
    public record Row(String profileId, String featureKey, boolean enabled) {}

    private final JdbcTemplate jdbcTemplate;

    public ProfileFeatureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Upsert de uma flag (profile_id, feature_key) → enabled. Carimba updated_at/by. */
    public void upsert(String profileId, String featureKey, boolean enabled, UUID updatedBy) {
        jdbcTemplate.update(
            "insert into profile_features (profile_id, feature_key, enabled, updated_by, updated_at) "
                + "values (?, ?, ?, ?, now()) "
                + "on conflict (profile_id, feature_key) do update set "
                + "enabled = excluded.enabled, updated_by = excluded.updated_by, updated_at = now()",
            profileId, featureKey, enabled, updatedBy);
    }

    /** Keys das features LIGADAS (enabled=true) para um nicho. Ausência = não está no set = OFF. */
    public Set<String> enabledKeysFor(String profileId) {
        List<String> keys = jdbcTemplate.queryForList(
            "select feature_key from profile_features where profile_id = ? and enabled = true",
            String.class, profileId);
        return new LinkedHashSet<>(keys);
    }

    /** Todas as linhas (desvios) — usado pelo service pra montar a grade completa. */
    public List<Row> allRows() {
        return jdbcTemplate.query(
            "select profile_id, feature_key, enabled from profile_features",
            (rs, rn) -> new Row(rs.getString("profile_id"), rs.getString("feature_key"), rs.getBoolean("enabled")));
    }
}
