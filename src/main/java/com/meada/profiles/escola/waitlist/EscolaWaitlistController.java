package com.meada.profiles.escola.waitlist;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.common.audit.AuditLogger;
import com.meada.profiles.escola.EscolaProfileGuard;
import com.meada.profiles.escola.EscolaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lista de espera por turma (onda Escola 1, backlog #1). TENANT + perfil 'escola'. Avisar o 1º
 * da fila é o botão humano do painel (a IA nunca promete vaga).
 */
@RestController
public class EscolaWaitlistController {

    private static final Set<String> STATUSES = Set.of("convertida", "desistiu");

    private final EscolaWaitlistService service;
    private final EscolaProfileGuard profileGuard;
    private final AuditLogger auditLogger;

    public EscolaWaitlistController(EscolaWaitlistService service, EscolaProfileGuard profileGuard,
                                    AuditLogger auditLogger) {
        this.service = service;
        this.profileGuard = profileGuard;
        this.auditLogger = auditLogger;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    @GetMapping("/api/escola/waitlist")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam UUID classId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.listByClass(companyId, classId)));
    }

    @PostMapping("/api/escola/waitlist/{id}/notify")
    public ResponseEntity<Object> notifyOpening(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.notifyOpening(companyId, id)
            .<ResponseEntity<Object>>map(w -> {
                auditLogger.log(companyId, user.userId(), "escola_waitlist_notified", "escola_waitlist",
                    id, Map.of());
                return ResponseEntity.ok(Map.of("notified", true));
            })
            .orElseGet(() -> error(404, "Not Found", "waitlist_entry_not_found"));
    }

    public record StatusRequest(String status) {}

    @PatchMapping("/api/escola/waitlist/{id}")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (req.status() == null || !STATUSES.contains(req.status())) {
            return error(400, "Bad Request", "invalid_status");
        }
        if (!service.updateStatus(companyId, id, req.status())) {
            return error(404, "Not Found", "waitlist_entry_not_found");
        }
        auditLogger.log(companyId, user.userId(), "escola_waitlist_updated", "escola_waitlist", id,
            Map.of("status", req.status()));
        return ResponseEntity.ok(Map.of("updated", true));
    }
}
