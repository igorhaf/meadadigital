package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;

import java.util.UUID;

/**
 * Leitura de {@code ai_settings} (1:1 com company). Consumido pelo PromptBuilder.
 *
 * <p>Retorna FIELMENTE o que está no banco: {@link Optional#empty()} se o tenant
 * não configurou. Defaults neutros são responsabilidade do PromptBuilder.
 */
@Repository
public class AiSettingsRepository {

    private static final RowMapper<AiSettings> ROW_MAPPER = (rs, rowNum) ->
        new AiSettings(
            rs.getString("tone"),
            rs.getString("system_rules"),
            rs.getString("restrictions"),
            rs.getString("handoff_triggers"),
            rs.getString("model_provider"));

    private static final String SELECT_BY_COMPANY =
        "select tone, system_rules, restrictions, handoff_triggers, model_provider "
            + "from ai_settings where company_id = ?";

    private final JdbcTemplate jdbcTemplate;

    public AiSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Settings do tenant, ou empty se não configurado (UNIQUE garante ≤ 1 linha). */
    public Optional<AiSettings> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(SELECT_BY_COMPANY, ROW_MAPPER, companyId)
            .stream()
            .findFirst();
    }
}
