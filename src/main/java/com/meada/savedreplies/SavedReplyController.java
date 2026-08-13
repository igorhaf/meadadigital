package com.meada.savedreplies;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CRUD de respostas prontas do painel do TENANT (camada 5.22 #88). TENANT-ADMIN ONLY: o
 * admin gerencia as respostas da PRÓPRIA empresa. Sob /admin/** (o JwtAuthenticationFilter
 * autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual InvitationController):
 * super-admin não tem company (companyId null) → 403 forbidden_not_tenant_admin.
 * Isolamento por empresa vem do companyId do próprio authenticatedUser (nunca de input
 * do cliente).
 */
@RestController
public class SavedReplyController {

    private final SavedReplyRepository repository;

    public SavedReplyController(SavedReplyRepository repository) {
        this.repository = repository;
    }

    /** Lista as respostas prontas da empresa do admin. */
    @GetMapping("/admin/saved-replies")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        List<Map<String, Object>> body = repository.findByCompany(user.companyId()).stream()
            .map(this::toJson)
            .toList();
        return ResponseEntity.ok(body);
    }

    /** Cria uma resposta pronta. Body {title, body}. 201 com a linha criada. */
    @PostMapping("/admin/saved-replies")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        SavedReply created = repository.insert(
            user.companyId(), request.get("title"), request.get("body"));
        return ResponseEntity.status(201).body(toJson(created));
    }

    /** Atualiza uma resposta pronta. Body {title, body}. 204 em sucesso; 404 se não achar. */
    @PutMapping("/admin/saved-replies/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        boolean updated = repository.update(
            id, user.companyId(), request.get("title"), request.get("body"));
        if (!updated) {
            return notFound();
        }
        return ResponseEntity.noContent().build();
    }

    /** Remove uma resposta pronta. 204 em sucesso; 404 se não achar. */
    @DeleteMapping("/admin/saved-replies/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        boolean deleted = repository.delete(id, user.companyId());
        if (!deleted) {
            return notFound();
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    private ResponseEntity<Object> notFound() {
        return ResponseEntity.status(404)
            .body(Map.of("error", "Not Found", "reason", "saved_reply_not_found"));
    }

    /** Serializa a resposta pronta para JSON (camelCase). */
    private Map<String, Object> toJson(SavedReply reply) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", reply.id().toString());
        m.put("title", reply.title());
        m.put("body", reply.body());
        m.put("createdAt", reply.createdAt().toString());
        return m;
    }
}
