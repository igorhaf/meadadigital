package com.meada.whatsapp.teams;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de times/departamentos do painel do TENANT (camada 5.20 #76). TENANT-ADMIN
 * ONLY: o admin gerencia os times da PRÓPRIA empresa. Sob /admin/** (o
 * JwtAuthenticationFilter autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4/5.16, igual InvitationController):
 * super-admin não tem company (companyId null) → não opera times de tenant → 403
 * forbidden_not_tenant_admin. Isolamento por empresa vem do companyId do próprio
 * authenticatedUser (nunca de input do cliente).
 */
@RestController
public class TeamController {

    private final TeamRepository repository;

    public TeamController(TeamRepository repository) {
        this.repository = repository;
    }

    /** Lista os times da empresa do admin. */
    @GetMapping("/admin/teams")
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

    /**
     * Cria um time. Body {name}. 201 com o time criado. Nome vazio/longo demais → o CHECK
     * do banco rejeita (vira 500 do GlobalExceptionHandler — o zod do frontend é a 1ª
     * barreira; aqui não há validação dedicada).
     */
    @PostMapping("/admin/teams")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        Team created = repository.insert(user.companyId(), trimmed(request.get("name")));
        return ResponseEntity.status(201).body(toJson(created));
    }

    /** Renomeia um time. Body {name}. 200 com o time atualizado; 404 se não encontrado. */
    @PutMapping("/admin/teams/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        boolean updated = repository.update(id, user.companyId(), trimmed(request.get("name")));
        if (!updated) {
            return notFound();
        }
        // Relê para devolver a linha consistente (com created_at). findByCompany filtra a
        // própria empresa; o id é único.
        return repository.findByCompany(user.companyId()).stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .<ResponseEntity<Object>>map(t -> ResponseEntity.ok(toJson(t)))
            .orElseGet(this::notFound);
    }

    /** Remove um time. 204 em sucesso; 404 se não encontrado. */
    @DeleteMapping("/admin/teams/{id}")
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

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    private ResponseEntity<Object> notFound() {
        return ResponseEntity.status(404)
            .body(Map.of("error", "Not Found", "reason", "team_not_found"));
    }

    /** Serializa o time para a resposta JSON (camelCase). */
    private Map<String, Object> toJson(Team team) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", team.id().toString());
        m.put("name", team.name());
        m.put("createdAt", team.createdAt().toString());
        return m;
    }
}
