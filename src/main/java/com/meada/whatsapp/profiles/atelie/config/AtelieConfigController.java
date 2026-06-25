package com.meada.whatsapp.profiles.atelie.config;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.atelie.AtelieProfileGuard;
import com.meada.whatsapp.profiles.atelie.AtelieProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** Config do tenant atelie (camada 8.14). TENANT + perfil 'atelie' only. GET (fallback) + PUT. */
@RestController
public class AtelieConfigController {

    private final AtelieConfigService service;
    private final AtelieProfileGuard profileGuard;

    public AtelieConfigController(AtelieConfigService service, AtelieProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(String businessName, String notes) {}

    @GetMapping("/api/atelie/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/atelie/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String businessName = req.businessName() == null || req.businessName().isBlank() ? null : req.businessName().trim();
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes();
        return ResponseEntity.ok(service.update(companyId, user.userId(), businessName, notes));
    }
}
