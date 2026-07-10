package com.meada.profiles.casamento.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.casamento.CasamentoProfileGuard;
import com.meada.profiles.casamento.CasamentoProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Config do tenant casamento (camada 8.7). TENANT + perfil 'casamento' only. GET (fallback) + PUT. */
@RestController
public class WeddingConfigController {

    private final WeddingConfigService service;
    private final CasamentoProfileGuard profileGuard;

    public WeddingConfigController(WeddingConfigService service, CasamentoProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(String businessName, String notes, Boolean checklistReminderEnabled,
                                Boolean paymentReminderEnabled, Boolean autoCompleteEnabled,
                                Boolean anniversaryEnabled, Boolean postEventEnabled, String reviewLink,
                                Boolean followUpEnabled, Integer followUpDays) {}

    @GetMapping("/api/casamento/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/casamento/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String businessName = req.businessName() == null || req.businessName().isBlank() ? null : req.businessName().trim();
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes();
        // ausente no payload = mantém o default LIGADO (toggles são opt-out).
        return ResponseEntity.ok(service.update(companyId, user.userId(), businessName, notes,
            req.checklistReminderEnabled() == null || req.checklistReminderEnabled(),
            req.paymentReminderEnabled() == null || req.paymentReminderEnabled(),
            req.autoCompleteEnabled() == null || req.autoCompleteEnabled(),
            req.anniversaryEnabled() == null || req.anniversaryEnabled(),
            req.postEventEnabled() == null || req.postEventEnabled(),
            req.reviewLink(),
            req.followUpEnabled() == null || req.followUpEnabled(),
            req.followUpDays() == null ? 5 : req.followUpDays()));
    }
}
