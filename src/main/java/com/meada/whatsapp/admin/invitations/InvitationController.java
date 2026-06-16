package com.meada.whatsapp.admin.invitations;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de convite do painel do TENANT (camada 5.16 #6). TENANT-ADMIN ONLY: o admin
 * convida outros admins para a PRÓPRIA empresa. Sob /admin/** (o JwtAuthenticationFilter
 * autentica e popula authenticatedUser).
 *
 * <p>Autorização por role no método (padrão da camada 4, igual CompanyAdminController):
 * super-admin não tem company (companyId null) → não opera convites de tenant → 403
 * forbidden_not_tenant_admin. Isolamento por empresa vem do companyId do próprio
 * authenticatedUser (nunca de input do cliente).
 */
@RestController
public class InvitationController {

    private final InvitationService invitationService;
    private final TenantInvitationRepository repository;
    private final String frontendBaseUrl;

    public InvitationController(InvitationService invitationService,
                               TenantInvitationRepository repository,
                               @Value("${invitations.frontend-base-url:http://localhost:3000}")
                               String frontendBaseUrl) {
        this.invitationService = invitationService;
        this.repository = repository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /** Lista os convites da empresa do admin (inclui usados/expirados — histórico). */
    @GetMapping("/admin/invitations")
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
     * Cria um convite. Body {email}. 201 com {id, email, token, expiresAt, inviteUrl}.
     * Email inválido → 400 invalid_email (InvalidInvitationEmailException).
     */
    @PostMapping("/admin/invitations")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody Map<String, String> request) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        try {
            TenantInvitation created = invitationService.createInvitation(
                user.companyId(), user.userId(), request.get("email"));
            return ResponseEntity.status(201).body(toJson(created));
        } catch (InvalidInvitationEmailException e) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Bad Request", "reason", "invalid_email"));
        }
    }

    /** Cancela um convite (expira imediatamente). 204 em sucesso; 404 se não encontrado. */
    @DeleteMapping("/admin/invitations/{id}")
    public ResponseEntity<Object> cancel(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        boolean cancelled = repository.cancel(id, user.companyId());
        if (!cancelled) {
            return ResponseEntity.status(404)
                .body(Map.of("error", "Not Found", "reason", "invitation_not_found"));
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    /** Serializa o convite para a resposta JSON (camelCase + inviteUrl composta). */
    private Map<String, Object> toJson(TenantInvitation inv) {
        java.util.HashMap<String, Object> m = new java.util.HashMap<>();
        m.put("id", inv.id().toString());
        m.put("email", inv.email());
        m.put("token", inv.token());
        m.put("inviteUrl", frontendBaseUrl + "/invite/" + inv.token());
        m.put("createdAt", inv.createdAt().toString());
        m.put("expiresAt", inv.expiresAt().toString());
        m.put("usedAt", inv.usedAt() != null ? inv.usedAt().toString() : null);
        m.put("usedBy", inv.usedBy() != null ? inv.usedBy().toString() : null);
        return m;
    }
}
