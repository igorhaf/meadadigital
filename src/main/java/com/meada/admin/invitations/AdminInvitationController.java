package com.meada.admin.invitations;

import com.meada.admin.audit.AdminAction;
import com.meada.admin.audit.AdminActionLogger;
import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * Convites GLOBAIS (cross-tenant) do painel super-admin (camada 6.2). SUPER-ADMIN ONLY.
 * Distinto do {@link InvitationController} (tenant, /admin/invitations): este é
 * /admin/invitations/all + /admin/invitations/{id}/revoke (sem colidir com o DELETE do
 * tenant).
 *
 * <p>Status derivado das colunas (sem coluna 'status' na tabela): used_at→accepted,
 * revoked_at→revoked, expires_at<now→expired, senão pending.
 */
@RestController
public class AdminInvitationController {

    private final JdbcTemplate jdbcTemplate;
    private final AdminActionLogger logger;

    public AdminInvitationController(JdbcTemplate jdbcTemplate, AdminActionLogger logger) {
        this.jdbcTemplate = jdbcTemplate;
        this.logger = logger;
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
    }

    @GetMapping("/admin/invitations/all")
    public ResponseEntity<Object> listAll(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String createdAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (user.role() != AdminRole.SUPER_ADMIN) {
            return forbidden();
        }
        int size = Math.min(Math.max(pageSize, 1), 200);

        // Status derivado em SQL para permitir filtrar por ele.
        String statusExpr =
            "case when i.used_at is not null then 'accepted' "
                + "when i.revoked_at is not null then 'revoked' "
                + "when i.expires_at < now() then 'expired' else 'pending' end";

        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (companyId != null) {
            where.append(" and i.company_id = ?");
            args.add(companyId);
        }
        if (createdAfter != null && !createdAfter.isBlank()) {
            try {
                where.append(" and i.created_at >= ?");
                args.add(Timestamp.from(Instant.parse(createdAfter)));
            } catch (DateTimeParseException e) {
                return ResponseEntity.status(400)
                    .body(Map.of("error", "Bad Request", "reason", "invalid_created_after"));
            }
        }
        if (status != null && !status.isBlank()) {
            where.append(" and ").append(statusExpr).append(" = ?");
            args.add(status.trim());
        }

        Long total = jdbcTemplate.queryForObject(
            "select count(*) from tenant_invitations i" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) Math.max(page, 0) * size);
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select i.id, i.email, i.created_at, i.expires_at, i.used_at, i.revoked_at, "
                + "c.name as company_name, " + statusExpr + " as status "
                + "from tenant_invitations i join companies c on c.id = i.company_id"
                + where + " order by i.created_at desc limit ? offset ?",
            (rs, rowNum) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("email", rs.getString("email"));
                m.put("companyName", rs.getString("company_name"));
                m.put("status", rs.getString("status"));
                m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                m.put("expiresAt", rs.getTimestamp("expires_at").toInstant().toString());
                return m;
            },
            pageArgs.toArray());

        return ResponseEntity.ok(Map.of(
            "items", items, "total", total == null ? 0L : total, "page", Math.max(page, 0), "pageSize", size));
    }

    @PostMapping("/admin/invitations/{id}/revoke")
    @Transactional
    public ResponseEntity<Object> revoke(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user.role() != AdminRole.SUPER_ADMIN) {
            return forbidden();
        }
        // Estado atual para decidir 404/409.
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                "select used_at, revoked_at from tenant_invitations where id = ?", id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "invitation_not_found"));
        }
        if (row.get("used_at") != null) {
            return ResponseEntity.status(409).body(Map.of("error", "Conflict", "reason", "invitation_already_used"));
        }
        if (row.get("revoked_at") != null) {
            return ResponseEntity.status(409).body(Map.of("error", "Conflict", "reason", "invitation_already_revoked"));
        }
        logger.log(user.userId(), AdminAction.INVITATION_REVOKED, AdminAction.TARGET_INVITATION, id, Map.of());
        jdbcTemplate.update("update tenant_invitations set revoked_at = now() where id = ?", id);
        return ResponseEntity.noContent().build();
    }
}
