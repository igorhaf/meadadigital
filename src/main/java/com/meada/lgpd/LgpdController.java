package com.meada.lgpd;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints LGPD do painel do TENANT (camada 5.24 #89 erase, #90 export). TENANT-ADMIN ONLY:
 * opera sobre contatos da PRÓPRIA empresa. Sob /admin/** (o JwtAuthenticationFilter autentica
 * e popula authenticatedUser). O companyId vem do usuário autenticado, nunca de input — é o
 * que isola o tenant (o contato de outra empresa cai em 404 contact_not_found).
 */
@RestController
public class LgpdController {

    private final LgpdService lgpdService;

    public LgpdController(LgpdService lgpdService) {
        this.lgpdService = lgpdService;
    }

    /**
     * Exporta todos os dados do contato (#90) como JSON (contato + conversas + mensagens +
     * agendamentos + tags). 200 com o objeto; 404 contact_not_found se não for da empresa.
     */
    @GetMapping("/admin/contacts/{id}/export")
    public ResponseEntity<Object> export(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(lgpdService.exportContact(user.companyId(), id));
        } catch (ContactNotFoundException e) {
            return notFound();
        }
    }

    /**
     * Apaga DEFINITIVAMENTE (hard delete) o contato e tudo dele (#89). 204 em sucesso; 404
     * contact_not_found se não for da empresa. Registra o apagamento em audit_log.
     */
    @DeleteMapping("/admin/contacts/{id}/erase")
    public ResponseEntity<Object> erase(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (user.role() != AdminRole.TENANT_ADMIN || user.companyId() == null) {
            return forbidden();
        }
        try {
            lgpdService.eraseContact(user.companyId(), id, user.userId());
            return ResponseEntity.noContent().build();
        } catch (ContactNotFoundException e) {
            return notFound();
        }
    }

    private ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Forbidden", "reason", "forbidden_not_tenant_admin"));
    }

    private ResponseEntity<Object> notFound() {
        return ResponseEntity.status(404)
            .body(Map.of("error", "Not Found", "reason", "contact_not_found"));
    }
}
