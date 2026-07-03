package com.meada.profiles.barbearia.loyalty;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.barbearia.BarberProfileGuard;
import com.meada.profiles.barbearia.BarberProfileGuard.WrongProfileException;
import com.meada.profiles.barbearia.loyalty.BarberLoyaltyConfigService.InvalidLoyaltyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Fidelidade "a cada N cortes, 1 grátis" do tenant barbearia (onda 1, backlog #3). TENANT + perfil
 * 'barbearia' only. GET (fallback desligada) + PUT.
 */
@RestController
public class BarberLoyaltyConfigController {

    private final BarberLoyaltyConfigService service;
    private final BarberProfileGuard profileGuard;

    public BarberLoyaltyConfigController(BarberLoyaltyConfigService service, BarberProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record LoyaltyRequest(boolean enabled, int thresholdCuts) {}

    @GetMapping("/api/barbearia/loyalty")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/barbearia/loyalty")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody LoyaltyRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), req.enabled(), req.thresholdCuts()));
        } catch (InvalidLoyaltyException e) {
            return error(400, "Bad Request", "invalid_loyalty");
        }
    }
}
