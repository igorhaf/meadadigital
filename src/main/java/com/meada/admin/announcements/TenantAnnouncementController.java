package com.meada.admin.announcements;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anúncios do ponto de vista do usuário logado (camada 6.7). QUALQUER usuário autenticado
 * (tenant ou super-admin) — não é super-admin-only: é o feed que o banner do AppShell consome.
 *
 * <p>O backend opera como service_role (BYPASSRLS); por isso filtramos explicitamente por
 * {@code user.userId()} no SQL (o que a policy de RLS faria para o tenant via SDK). Lista só
 * anúncios publicados, não-expirados E não-dispensados pelo usuário atual. dismiss faz upsert
 * idempotente em announcement_dismissals.
 */
@RestController
public class TenantAnnouncementController {

    private final JdbcTemplate jdbcTemplate;

    public TenantAnnouncementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- GET feed do usuário ------------------------------------------------
    @GetMapping("/admin/me/announcements")
    public ResponseEntity<Object> myAnnouncements(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID userId = user.userId();
        // publicados, não-expirados, e SEM dismissal deste usuário. Inclui severity p/ a UI colorir.
        List<Map<String, Object>> items = jdbcTemplate.query(
            "select a.id, a.title, a.body, a.severity, a.published_at, a.expires_at, a.dismissable "
                + "from announcements a "
                + "where a.published_at <= now() and (a.expires_at is null or a.expires_at > now()) "
                + "and not exists (select 1 from announcement_dismissals d "
                + "                where d.announcement_id = a.id and d.user_id = ?) "
                + "order by a.published_at desc",
            (rs, rn) -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", rs.getString("id"));
                m.put("title", rs.getString("title"));
                m.put("body", rs.getString("body"));
                m.put("severity", rs.getString("severity"));
                m.put("publishedAt", rs.getTimestamp("published_at").toInstant().toString());
                Timestamp exp = rs.getTimestamp("expires_at");
                m.put("expiresAt", exp != null ? exp.toInstant().toString() : null);
                m.put("dismissable", rs.getBoolean("dismissable"));
                return m;
            }, userId);
        return ResponseEntity.ok(Map.of("items", items));
    }

    // ---- POST dismiss (upsert idempotente) ----------------------------------
    @PostMapping("/admin/me/announcements/{id}/dismiss")
    public ResponseEntity<Object> dismiss(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        // upsert: re-dismiss do mesmo par (announcement, user) é no-op (unique). 204 sempre.
        jdbcTemplate.update(
            "insert into announcement_dismissals (announcement_id, user_id) values (?, ?) "
                + "on conflict (announcement_id, user_id) do nothing",
            id, user.userId());
        return ResponseEntity.noContent().build();
    }
}
