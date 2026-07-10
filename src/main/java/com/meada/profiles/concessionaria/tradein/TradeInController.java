package com.meada.profiles.concessionaria.tradein;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.common.audit.AuditLogger;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Trade-in (onda Concessionária 2, backlog #5). TENANT + perfil 'concessionaria'. */
@RestController
public class TradeInController {

    private static final Set<String> STATUSES = Set.of("aberta", "avaliada", "aceita", "recusada");

    private final TradeInService service;
    private final ConcessionariaProfileGuard profileGuard;
    private final AuditLogger auditLogger;

    public TradeInController(TradeInService service, ConcessionariaProfileGuard profileGuard,
                             AuditLogger auditLogger) {
        this.service = service;
        this.profileGuard = profileGuard;
        this.auditLogger = auditLogger;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    @GetMapping("/api/concessionaria/tradein")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            return error(400, "Bad Request", "invalid_status");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, status)));
    }

    public record UpdateRequest(String status, Integer offerCents, String notes) {}

    @PatchMapping("/api/concessionaria/tradein/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (req.status() != null && !req.status().isBlank() && !STATUSES.contains(req.status())) {
            return error(400, "Bad Request", "invalid_status");
        }
        if (!service.update(companyId, id,
                req.status() == null || req.status().isBlank() ? null : req.status(),
                req.offerCents(), req.notes())) {
            return error(404, "Not Found", "tradein_not_found");
        }
        auditLogger.log(companyId, user.userId(), "concessionaria_tradein_updated",
            "concessionaria_tradein", id, Map.of());
        return ResponseEntity.ok(Map.of("updated", true));
    }
}
