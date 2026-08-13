package com.meada.admin.users;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Usuários globais do painel super-admin (camada 6.2). SUPER-ADMIN ONLY (check manual de
 * role, padrão da camada 4). Listagem/detalhe + suspender/reativar/reset-senha/excluir.
 *
 * <p>password-reset depende de SUPABASE_SERVICE_ROLE_KEY (segredo do Igor, ausente hoje):
 * sem a chave, retorna 501 sem efeito. Quando configurada, vira chamada real à Admin API
 * do Supabase (fase futura).
 */
@RestController
public class UserAdminController {

    private final UserAdminService service;
    private final String serviceRoleKey;

    public UserAdminController(UserAdminService service,
                              @Value("${supabase.service-role-key:}") String serviceRoleKey) {
        this.service = service;
        this.serviceRoleKey = serviceRoleKey;
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
    }

    private static boolean notSuper(AuthenticatedUser user) {
        return user.role() != AdminRole.SUPER_ADMIN;
    }

    @GetMapping("/admin/users")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID companyId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean suspended,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (notSuper(user)) {
            return forbidden();
        }
        int size = Math.min(Math.max(pageSize, 1), 200);
        UserAdminService.UserPage p = service.list(q, companyId, role, suspended, Math.max(page, 0), size);
        List<Map<String, Object>> items = p.items().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.id().toString());
            m.put("email", u.email());
            m.put("role", u.role());
            m.put("companyName", u.companyName());
            m.put("suspended", u.suspended());
            m.put("lastLoginAt", u.lastLoginAt() != null ? u.lastLoginAt().toString() : null);
            m.put("createdAt", u.createdAt().toString());
            return m;
        }).toList();
        return ResponseEntity.ok(Map.of("items", items, "total", p.total(), "page", p.page(), "pageSize", p.pageSize()));
    }

    @GetMapping("/admin/users/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) {
            return forbidden();
        }
        return service.detail(id).<ResponseEntity<Object>>map(d -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", d.id().toString());
            m.put("email", d.email());
            m.put("role", d.role());
            m.put("companyId", d.companyId().toString());
            m.put("companyName", d.companyName());
            m.put("suspended", d.suspended());
            m.put("suspendedAt", d.suspendedAt() != null ? d.suspendedAt().toString() : null);
            m.put("suspendedReason", d.suspendedReason());
            m.put("lastLoginAt", d.lastLoginAt() != null ? d.lastLoginAt().toString() : null);
            m.put("createdAt", d.createdAt().toString());
            m.put("recentActions", service.recentActions(id).stream().map(a -> {
                Map<String, Object> am = new HashMap<>();
                am.put("action", a.action());
                am.put("payload", a.payload());
                am.put("createdAt", a.createdAt().toString());
                return am;
            }).toList());
            return ResponseEntity.ok(m);
        }).orElseGet(() -> ResponseEntity.status(404)
            .body(Map.of("error", "Not Found", "reason", "user_not_found")));
    }

    @PostMapping("/admin/users/{id}/suspend")
    public ResponseEntity<Object> suspend(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        if (notSuper(user)) {
            return forbidden();
        }
        String reason = body == null ? null : body.get("reason");
        try {
            boolean ok = service.suspend(user.userId(), id, reason);
            return ok ? ResponseEntity.noContent().build()
                : ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "user_not_found"));
        } catch (UserAdminService.AlreadySuspendedException e) {
            return ResponseEntity.status(409).body(Map.of("error", "Conflict", "reason", "user_already_suspended"));
        }
    }

    @PostMapping("/admin/users/{id}/reactivate")
    public ResponseEntity<Object> reactivate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) {
            return forbidden();
        }
        boolean ok = service.reactivate(user.userId(), id);
        return ok ? ResponseEntity.noContent().build()
            : ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "user_not_found"));
    }

    @PostMapping("/admin/users/{id}/password-reset")
    public ResponseEntity<Object> passwordReset(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) {
            return forbidden();
        }
        // Sem SUPABASE_SERVICE_ROLE_KEY não há como o backend disparar reset via Admin API.
        // 501 honesto, sem efeito e sem log (nada aconteceu).
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            return ResponseEntity.status(501).body(Map.of(
                "error", "Not Implemented", "reason", "service_role_key_not_configured"));
        }
        // (Quando a chave existir: chamar Supabase Auth Admin API + logger.log(USER_PASSWORD_RESET).)
        return ResponseEntity.status(501).body(Map.of(
            "error", "Not Implemented", "reason", "service_role_key_not_configured"));
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<Object> softDelete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) {
            return forbidden();
        }
        boolean ok = service.softDelete(user.userId(), id);
        return ok ? ResponseEntity.noContent().build()
            : ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "user_not_found"));
    }
}
