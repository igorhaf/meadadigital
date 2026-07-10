package com.meada.profiles.eventos.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.eventos.EventosProfileGuard;
import com.meada.profiles.eventos.EventosProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Config do tenant eventos (camada 8.2). TENANT + perfil 'eventos' only. GET (fallback) + PUT. */
@RestController
public class EventConfigController {

    private final EventConfigService service;
    private final EventosProfileGuard profileGuard;

    public EventConfigController(EventConfigService service, EventosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(String businessName, String notes, Boolean autoCompleteEnabled,
                                Boolean postEventEnabled, String reviewLink,
                                Boolean followUpEnabled, Integer followUpDays) {}

    @GetMapping("/api/eventos/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/eventos/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String businessName = req.businessName() == null || req.businessName().isBlank() ? null : req.businessName().trim();
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes();
        return ResponseEntity.ok(service.update(companyId, user.userId(), businessName, notes,
            req.autoCompleteEnabled() == null || req.autoCompleteEnabled(),
            req.postEventEnabled() == null || req.postEventEnabled(),
            req.reviewLink(),
            req.followUpEnabled() == null || req.followUpEnabled(),
            req.followUpDays() == null ? 3 : req.followUpDays()));
    }
}
