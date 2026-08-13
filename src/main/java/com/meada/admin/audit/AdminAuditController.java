package com.meada.admin.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Visões GLOBAIS (cross-tenant) de auditoria/segurança/ações-admin para o super-admin
 * (camada 6.5). SUPER-ADMIN ONLY. READ-only (sem mutações, sem AdminActionLogger).
 *
 * <p>audit_log e access_logs já existem per-company (telas do tenant); aqui o super-admin
 * vê tudo, sem filtro de tenant. admin_action_log é o rastro do próprio super-admin.
 * Todos paginados {items,total,page,pageSize}.
 */
@RestController
public class AdminAuditController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AdminAuditController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private static boolean notSuper(AuthenticatedUser u) {
        return u.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
    }

    /** jsonb (text) → objeto JS no JSON de saída; null-safe. */
    private Object parseJson(String raw) {
        if (raw == null) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    /** Parse ISO → Timestamp; lança para o caller virar 400. */
    private Timestamp parseTs(String iso) {
        return Timestamp.from(Instant.parse(iso));
    }

    // -------------------------------------------------------------------------
    // GET /admin/audit/all — audit_log global
    // -------------------------------------------------------------------------
    @GetMapping("/admin/audit/all")
    public ResponseEntity<Object> auditAll(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entity,
            @RequestParam(required = false) String createdAfter,
            @RequestParam(required = false) String createdBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (notSuper(user)) return forbidden();
        int size = Math.min(Math.max(pageSize, 1), 200);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (companyId != null) { where.append(" and a.company_id = ?"); args.add(companyId); }
        if (userId != null) { where.append(" and a.user_id = ?"); args.add(userId); }
        if (action != null && !action.isBlank()) { where.append(" and a.action = ?"); args.add(action.trim()); }
        if (entity != null && !entity.isBlank()) { where.append(" and a.entity = ?"); args.add(entity.trim()); }
        try {
            if (createdAfter != null && !createdAfter.isBlank()) { where.append(" and a.created_at >= ?"); args.add(parseTs(createdAfter)); }
            if (createdBefore != null && !createdBefore.isBlank()) { where.append(" and a.created_at <= ?"); args.add(parseTs(createdBefore)); }
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(400).body(Map.of("error", "Bad Request", "reason", "invalid_date"));
        }

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from audit_log a" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add((long) Math.max(page, 0) * size);
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select a.id, a.company_id, c.name as company_name, a.user_id, a.action, a.entity, "
                + "a.entity_id, a.metadata, a.created_at from audit_log a "
                + "left join companies c on c.id = a.company_id" + where
                + " order by a.created_at desc limit ? offset ?",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("companyName", rs.getString("company_name"));
                m.put("userId", rs.getObject("user_id") != null ? rs.getObject("user_id").toString() : null);
                m.put("action", rs.getString("action"));
                m.put("entity", rs.getString("entity"));
                m.put("entityId", rs.getObject("entity_id") != null ? rs.getObject("entity_id").toString() : null);
                m.put("metadata", parseJson(rs.getString("metadata")));
                m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                return m;
            }, pageArgs.toArray());
        return ResponseEntity.ok(Map.of("items", items, "total", total == null ? 0L : total,
            "page", Math.max(page, 0), "pageSize", size));
    }

    // -------------------------------------------------------------------------
    // GET /admin/security/access-logs/all — access_logs global
    // (access_logs tem coluna 'action' login_success|login_failed|password_changed, NÃO
    //  um bool 'success' — filtramos por action.)
    // -------------------------------------------------------------------------
    @GetMapping("/admin/security/access-logs/all")
    public ResponseEntity<Object> accessLogsAll(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String userAgent,
            @RequestParam(required = false) String createdAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (notSuper(user)) return forbidden();
        int size = Math.min(Math.max(pageSize, 1), 200);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (userId != null) { where.append(" and l.user_id = ?"); args.add(userId); }
        if (action != null && !action.isBlank()) { where.append(" and l.action = ?"); args.add(action.trim()); }
        if (ip != null && !ip.isBlank()) { where.append(" and l.ip = ?"); args.add(ip.trim()); }
        if (userAgent != null && !userAgent.isBlank()) { where.append(" and l.user_agent ilike ?"); args.add("%" + userAgent.trim() + "%"); }
        try {
            if (createdAfter != null && !createdAfter.isBlank()) { where.append(" and l.created_at >= ?"); args.add(parseTs(createdAfter)); }
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(400).body(Map.of("error", "Bad Request", "reason", "invalid_date"));
        }

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from access_logs l" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add((long) Math.max(page, 0) * size);
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select l.id, c.name as company_name, l.email, l.action, l.ip, l.user_agent, l.created_at "
                + "from access_logs l left join companies c on c.id = l.company_id" + where
                + " order by l.created_at desc limit ? offset ?",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("companyName", rs.getString("company_name"));
                m.put("email", rs.getString("email"));
                m.put("action", rs.getString("action"));
                m.put("ip", rs.getString("ip"));
                m.put("userAgent", rs.getString("user_agent"));
                m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                return m;
            }, pageArgs.toArray());
        return ResponseEntity.ok(Map.of("items", items, "total", total == null ? 0L : total,
            "page", Math.max(page, 0), "pageSize", size));
    }

    // -------------------------------------------------------------------------
    // GET /admin/actions — admin_action_log
    // -------------------------------------------------------------------------
    @GetMapping("/admin/actions")
    public ResponseEntity<Object> adminActions(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID superAdminUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) String createdAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (notSuper(user)) return forbidden();
        int size = Math.min(Math.max(pageSize, 1), 200);

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (superAdminUserId != null) { where.append(" and super_admin_user_id = ?"); args.add(superAdminUserId); }
        if (action != null && !action.isBlank()) { where.append(" and action = ?"); args.add(action.trim()); }
        if (targetType != null && !targetType.isBlank()) { where.append(" and target_type = ?"); args.add(targetType.trim()); }
        if (targetId != null) { where.append(" and target_id = ?"); args.add(targetId); }
        try {
            if (createdAfter != null && !createdAfter.isBlank()) { where.append(" and created_at >= ?"); args.add(parseTs(createdAfter)); }
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(400).body(Map.of("error", "Bad Request", "reason", "invalid_date"));
        }

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from admin_action_log" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size); pageArgs.add((long) Math.max(page, 0) * size);
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select id, super_admin_user_id, action, target_type, target_id, payload, created_at "
                + "from admin_action_log" + where + " order by created_at desc limit ? offset ?",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("superAdminUserId", rs.getObject("super_admin_user_id").toString());
                m.put("action", rs.getString("action"));
                m.put("targetType", rs.getString("target_type"));
                m.put("targetId", rs.getObject("target_id") != null ? rs.getObject("target_id").toString() : null);
                m.put("payload", parseJson(rs.getString("payload")));
                m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                return m;
            }, pageArgs.toArray());
        return ResponseEntity.ok(Map.of("items", items, "total", total == null ? 0L : total,
            "page", Math.max(page, 0), "pageSize", size));
    }
}
