package com.meada.admin.announcements;

import com.meada.admin.audit.AdminAction;
import com.meada.admin.audit.AdminActionLogger;
import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestão de anúncios cross-tenant pelo super-admin (camada 6.7). SUPER-ADMIN ONLY.
 *
 * <p>Anúncio = aviso para TODOS os tenants (info/warning/critical), exibido como banner no
 * AppShell. Fim de vida = expires_at: o DELETE é SOFT (expires_at=now()) — preserva o histórico
 * de dismissals e mantém a mesma semântica de "expirou agora".
 *
 * <p>Ações mutáveis logam no admin_action_log via AdminActionLogger (ANNOUNCEMENT_*). Padrão
 * {error, reason} para erros, igual aos demais controllers admin.
 */
@RestController
public class AdminAnnouncementController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminActionLogger actionLogger;

    public AdminAnnouncementController(JdbcTemplate jdbcTemplate, AdminActionLogger actionLogger) {
        this.jdbcTemplate = jdbcTemplate;
        this.actionLogger = actionLogger;
    }

    private static boolean notSuper(AuthenticatedUser u) {
        return u.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    private static ResponseEntity<Object> forbidden() {
        return error(403, "Forbidden", "forbidden_not_super_admin");
    }

    /** Request de criação. expiresAt/dismissable opcionais. */
    public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String body,
        String severity,
        String expiresAt,
        Boolean dismissable) {}

    /** Request de edição. Todos opcionais (PATCH parcial). */
    public record UpdateAnnouncementRequest(
        @Size(max = 200) String title,
        @Size(max = 5000) String body,
        String severity,
        String expiresAt,
        Boolean dismissable) {}

    private static boolean validSeverity(String s) {
        return "info".equals(s) || "warning".equals(s) || "critical".equals(s);
    }

    private Map<String, Object> rowToDto(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getString("id"));
        m.put("title", rs.getString("title"));
        m.put("body", rs.getString("body"));
        m.put("severity", rs.getString("severity"));
        m.put("publishedAt", rs.getTimestamp("published_at").toInstant().toString());
        Timestamp exp = rs.getTimestamp("expires_at");
        m.put("expiresAt", exp != null ? exp.toInstant().toString() : null);
        m.put("dismissable", rs.getBoolean("dismissable"));
        m.put("createdBy", rs.getObject("created_by") != null ? rs.getObject("created_by").toString() : null);
        return m;
    }

    // ---- GET lista (filtro ativo/expirado + paginação) ----------------------
    @GetMapping("/admin/announcements")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,   // "active" | "expired" | null=todos
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (notSuper(user)) return forbidden();
        int size = Math.min(Math.max(pageSize, 1), 200);

        StringBuilder where = new StringBuilder(" where 1=1");
        if ("active".equals(status)) {
            where.append(" and (expires_at is null or expires_at > now())");
        } else if ("expired".equals(status)) {
            where.append(" and expires_at is not null and expires_at <= now()");
        }

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from announcements" + where, Long.class);
        List<Object> args = new ArrayList<>();
        args.add(size); args.add((long) Math.max(page, 0) * size);
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select id, title, body, severity, published_at, expires_at, dismissable, created_by "
                + "from announcements" + where + " order by published_at desc limit ? offset ?",
            (rs, rn) -> rowToDto(rs), args.toArray());
        return ResponseEntity.ok(Map.of("items", items, "total", total == null ? 0L : total,
            "page", Math.max(page, 0), "pageSize", size));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/admin/announcements")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateAnnouncementRequest req) {
        if (notSuper(user)) return forbidden();
        String severity = (req.severity() == null || req.severity().isBlank()) ? "info" : req.severity();
        if (!validSeverity(severity)) return error(400, "Bad Request", "invalid_severity");
        Timestamp expiresAt;
        try {
            expiresAt = parseExpiry(req.expiresAt());
        } catch (DateTimeParseException e) {
            return error(400, "Bad Request", "invalid_expires_at");
        }
        boolean dismissable = req.dismissable() == null || req.dismissable();
        UUID id = jdbcTemplate.queryForObject(
            "insert into announcements (title, body, severity, expires_at, created_by, dismissable) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, req.title().trim(), req.body().trim(), severity, expiresAt,
            user.userId(), dismissable);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", req.title().trim());
        payload.put("severity", severity);
        actionLogger.log(user.userId(), AdminAction.ANNOUNCEMENT_CREATED,
            AdminAction.TARGET_ANNOUNCEMENT, id, payload);

        return ResponseEntity.status(201).body(findOne(id));
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/admin/announcements/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAnnouncementRequest req) {
        if (notSuper(user)) return forbidden();
        if (findOne(id) == null) return error(404, "Not Found", "announcement_not_found");

        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (req.title() != null && !req.title().isBlank()) { sets.add("title = ?"); args.add(req.title().trim()); }
        if (req.body() != null && !req.body().isBlank()) { sets.add("body = ?"); args.add(req.body().trim()); }
        if (req.severity() != null && !req.severity().isBlank()) {
            if (!validSeverity(req.severity())) return error(400, "Bad Request", "invalid_severity");
            sets.add("severity = ?"); args.add(req.severity());
        }
        if (req.expiresAt() != null) {
            try {
                sets.add("expires_at = ?"); args.add(parseExpiry(req.expiresAt()));
            } catch (DateTimeParseException e) {
                return error(400, "Bad Request", "invalid_expires_at");
            }
        }
        if (req.dismissable() != null) { sets.add("dismissable = ?"); args.add(req.dismissable()); }

        if (!sets.isEmpty()) {
            args.add(id);
            jdbcTemplate.update("update announcements set " + String.join(", ", sets) + " where id = ?",
                args.toArray());
            actionLogger.log(user.userId(), AdminAction.ANNOUNCEMENT_UPDATED,
                AdminAction.TARGET_ANNOUNCEMENT, id, Map.of("fields", sets.size()));
        }
        return ResponseEntity.ok(findOne(id));
    }

    // ---- DELETE (soft: expires_at = now()) ----------------------------------
    @DeleteMapping("/admin/announcements/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) return forbidden();
        int n = jdbcTemplate.update("update announcements set expires_at = now() where id = ?", id);
        if (n == 0) return error(404, "Not Found", "announcement_not_found");
        actionLogger.log(user.userId(), AdminAction.ANNOUNCEMENT_DELETED,
            AdminAction.TARGET_ANNOUNCEMENT, id, Map.of());
        return ResponseEntity.noContent().build();
    }

    /** expiresAt: null/vazio → null (sem expiração); senão ISO-8601 → Timestamp. */
    private Timestamp parseExpiry(String iso) {
        if (iso == null || iso.isBlank()) return null;
        return Timestamp.from(Instant.parse(iso));
    }

    /** Busca uma row por id, ou null. */
    private Map<String, Object> findOne(UUID id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "select id, title, body, severity, published_at, expires_at, dismissable, created_by "
                + "from announcements where id = ?",
            (rs, rn) -> rowToDto(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }
}
