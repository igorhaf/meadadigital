package com.meada.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Visualizador de audit log do painel do TENANT (camada 5.20 #78). TENANT-ADMIN ONLY: o
 * admin lê as ações auditadas da PRÓPRIA empresa. Sob /admin/** (o JwtAuthenticationFilter
 * autentica e popula authenticatedUser).
 *
 * <p>A tabela audit_log e o trigger app.audit_trigger já existem desde a fase-5.3 (este
 * endpoint é só leitura/UI — não há schema novo). Como o backend opera service_role (fora
 * do RLS), filtramos por companyId no WHERE (defesa em profundidade — a policy
 * audit_log_select_own também isola por company_id = app.company_id()).
 *
 * <p>Filtros opcionais entity/action e limite (cap 200). metadata é jsonb cru — devolvido
 * como objeto via objectMapper.readTree (não como string escapada).
 */
@RestController
public class AuditLogController {

    private static final Logger log = LoggerFactory.getLogger(AuditLogController.class);

    /** Teto duro do limit: protege contra varredura cara mesmo se o cliente pedir mais. */
    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_LIMIT = 50;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Lista as ações auditadas da empresa do admin, mais recentes primeiro. Filtros
     * opcionais entity/action (ignorados se vazios). limit é clampado em [1, 200].
     */
    @GetMapping("/admin/audit-logs")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer limit) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }

        int cappedLimit = clampLimit(limit);

        // Monta a query com filtros opcionais. Sempre isola por company_id (1º parâmetro).
        StringBuilder sql = new StringBuilder(
            "select id, user_id, action, entity, entity_id, metadata, created_at "
                + "from audit_log where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(user.companyId());
        if (entity != null && !entity.isBlank()) {
            sql.append(" and entity = ?");
            args.add(entity.trim());
        }
        if (action != null && !action.isBlank()) {
            sql.append(" and action = ?");
            args.add(action.trim());
        }
        sql.append(" order by created_at desc limit ?");
        args.add(cappedLimit);

        List<Map<String, Object>> body = jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> toJson(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("user_id"),
                rs.getString("action"),
                rs.getString("entity"),
                (UUID) rs.getObject("entity_id"),
                rs.getString("metadata"),
                rs.getTimestamp("created_at").toInstant().toString()),
            args.toArray());

        return ResponseEntity.ok(body);
    }

    /** Clampa o limit recebido em [1, MAX_LIMIT]; null → DEFAULT_LIMIT. */
    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    /**
     * Serializa uma linha de audit_log para JSON (camelCase). metadata vem como texto
     * jsonb do banco e é reparseado em objeto (readTree) para não viajar escapado como
     * string. Se o parse falhar (jsonb malformado — não esperado), loga WARN e devolve
     * null no campo (não quebra a listagem).
     */
    private Map<String, Object> toJson(UUID id, UUID userId, String action, String entity,
                                       UUID entityId, String metadata, String createdAt) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", id.toString());
        m.put("userId", userId != null ? userId.toString() : null);
        m.put("action", action);
        m.put("entity", entity);
        m.put("entityId", entityId != null ? entityId.toString() : null);
        m.put("metadata", parseMetadata(metadata));
        m.put("createdAt", createdAt);
        return m;
    }

    private JsonNode parseMetadata(String metadata) {
        if (metadata == null) {
            return null;
        }
        try {
            return objectMapper.readTree(metadata);
        } catch (JsonProcessingException e) {
            log.warn("audit log metadata jsonb malformado (engolido): {}", metadata, e);
            return null;
        }
    }
}
