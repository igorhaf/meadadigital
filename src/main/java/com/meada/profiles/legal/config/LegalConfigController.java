package com.meada.profiles.legal.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.common.audit.AuditLogger;
import com.meada.profiles.legal.LegalProfileGuard;
import com.meada.profiles.legal.LegalProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Config do tenant legal (onda 1): review link + toggles. GET (fallback) + PUT. */
@RestController
public class LegalConfigController {

    private final LegalConfigRepository repository;
    private final LegalProfileGuard profileGuard;
    private final AuditLogger auditLogger;

    public LegalConfigController(LegalConfigRepository repository, LegalProfileGuard profileGuard,
                                 AuditLogger auditLogger) {
        this.repository = repository;
        this.profileGuard = profileGuard;
        this.auditLogger = auditLogger;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(String reviewLink, Boolean postClosureEnabled,
                                Boolean deadlineReminderEnabled) {}

    @GetMapping("/api/legal/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLegal(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(repository.findByCompany(companyId));
    }

    @PutMapping("/api/legal/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLegal(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String link = req.reviewLink() == null || req.reviewLink().isBlank() ? null : req.reviewLink().trim();
        LegalConfig saved = repository.upsert(companyId, link,
            req.postClosureEnabled() == null || req.postClosureEnabled(),
            req.deadlineReminderEnabled() == null || req.deadlineReminderEnabled());
        auditLogger.log(companyId, user.userId(), "legal_config_updated", "legal_config", companyId, Map.of());
        return ResponseEntity.ok(saved);
    }
}
