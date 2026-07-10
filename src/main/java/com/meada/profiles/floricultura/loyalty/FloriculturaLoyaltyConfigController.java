package com.meada.profiles.floricultura.loyalty;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.floricultura.FloriculturaProfileGuard;
import com.meada.profiles.floricultura.FloriculturaProfileGuard.WrongProfileException;
import com.meada.profiles.floricultura.loyalty.FloriculturaLoyaltyConfigService.InvalidLoyaltyConfigException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config de fidelidade do tenant floricultura (camada 8.5, backlog #2 — clone do chassi sushi). TENANT +
 * perfil 'floricultura' only. GET (com fallback p/ defaults) + PUT (upsert). Sob {@code /api/floricultura/**}.
 */
@RestController
public class FloriculturaLoyaltyConfigController {

    private final FloriculturaLoyaltyConfigService service;
    private final FloriculturaProfileGuard profileGuard;

    public FloriculturaLoyaltyConfigController(FloriculturaLoyaltyConfigService service, FloriculturaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record UpdateRequest(
        @NotNull Boolean enabled,
        @NotNull Integer thresholdOrders,
        @NotBlank String rewardKind,
        @NotNull Integer rewardValue) {}

    @GetMapping("/api/floricultura/loyalty")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFloricultura(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/floricultura/loyalty")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFloricultura(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), req.enabled(),
                req.thresholdOrders(), req.rewardKind(), req.rewardValue()));
        } catch (InvalidLoyaltyConfigException e) {
            return error(400, "Bad Request", "invalid_loyalty_config");
        }
    }
}
