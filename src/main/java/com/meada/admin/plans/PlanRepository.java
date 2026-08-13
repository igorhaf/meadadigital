package com.meada.admin.plans;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.admin.plans.PlanDtos.PlanResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code plans} (camada 6.8). Opera via service_role. features (jsonb) é
 * serializado/desserializado com Jackson; limites null = ilimitado.
 */
@Repository
public class PlanRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<PlanResponse> mapper;

    public PlanRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mapper = buildMapper();
    }

    /** RowMapper de plan; isolado num método para capturar objectMapper já inicializado. */
    private RowMapper<PlanResponse> buildMapper() {
        return (rs, rn) -> {
        com.fasterxml.jackson.databind.JsonNode features;
        try {
            String raw = rs.getString("features");
            features = raw == null ? null : objectMapper.readTree(raw);
        } catch (Exception e) {
            features = null;
        }
        return new PlanResponse(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getInt("monthly_price_cents"),
            (Integer) rs.getObject("max_admins"),
            (Integer) rs.getObject("max_faqs"),
            (Integer) rs.getObject("max_conversations_month"),
            (Integer) rs.getObject("max_users"),
            features,
            rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant().toString(),
            rs.getTimestamp("updated_at").toInstant().toString());
        };
    }

    private static final String SELECT =
        "select id, name, slug, monthly_price_cents, max_admins, max_faqs, "
            + "max_conversations_month, max_users, features, active, created_at, updated_at from plans ";

    public List<PlanResponse> findAll() {
        return jdbcTemplate.query(SELECT + "order by monthly_price_cents asc, name asc", mapper);
    }

    public Optional<PlanResponse> findById(UUID id) {
        return jdbcTemplate.query(SELECT + "where id = ?", mapper, id).stream().findFirst();
    }

    /** Insere; features null → '{}'. DuplicateKeyException sobe se name/slug colidir. */
    public PlanResponse insert(PlanDtos.CreatePlanRequest req) {
        String featuresJson = featuresToJson(req.features());
        UUID id = jdbcTemplate.queryForObject(
            "insert into plans (name, slug, monthly_price_cents, max_admins, max_faqs, "
                + "max_conversations_month, max_users, features) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?::jsonb) returning id",
            UUID.class, req.name().trim(), req.slug().trim(),
            req.monthlyPriceCents() == null ? 0 : req.monthlyPriceCents(),
            req.maxAdmins(), req.maxFaqs(), req.maxConversationsMonth(), req.maxUsers(), featuresJson);
        return findById(id).orElseThrow();
    }

    /** Soft delete: active=false + touch updated_at. Retorna linhas afetadas. */
    public int softDelete(UUID id) {
        return jdbcTemplate.update(
            "update plans set active = false, updated_at = now() where id = ?", id);
    }

    private String featuresToJson(com.fasterxml.jackson.databind.JsonNode features) {
        if (features == null || features.isNull()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(features);
        } catch (Exception e) {
            return "{}";
        }
    }

    public JdbcTemplate jdbc() {
        return jdbcTemplate;
    }

    public String featuresJson(com.fasterxml.jackson.databind.JsonNode features) {
        return featuresToJson(features);
    }
}
