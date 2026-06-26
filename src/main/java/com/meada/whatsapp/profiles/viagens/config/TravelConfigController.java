package com.meada.whatsapp.profiles.viagens.config;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.viagens.ViagensProfileGuard;
import com.meada.whatsapp.profiles.viagens.ViagensProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant viagens (camada 8.18 / perfil viagens). TENANT + perfil 'viagens' only. GET
 * (fallback) + PUT. Espelho EXATO do EventConfigController (chassi eventos 8.2).
 */
@RestController
public class TravelConfigController {

    private final TravelConfigService service;
    private final ViagensProfileGuard profileGuard;

    public TravelConfigController(TravelConfigService service, ViagensProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(String businessName, String notes) {}

    @GetMapping("/api/viagens/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/viagens/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String businessName = req.businessName() == null || req.businessName().isBlank() ? null : req.businessName().trim();
        String notes = req.notes() == null || req.notes().isBlank() ? null : req.notes();
        return ResponseEntity.ok(service.update(companyId, user.userId(), businessName, notes));
    }
}
