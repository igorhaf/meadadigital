package com.meada.profiles.comida;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.comida.ComidaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant comida (camada 8.4): taxa de entrega + pedido mínimo. TENANT + perfil
 * 'comida' only. GET (fallback ZERO) + PATCH (upsert). Sob {@code /api/comida/config}.
 */
@RestController
public class ComidaConfigController {

    private final ComidaConfigService service;
    private final ComidaProfileGuard profileGuard;

    public ComidaConfigController(ComidaConfigService service, ComidaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(int deliveryFeeCents, int minOrderCents, String opensAt,
                                String closesAt, Integer autoDeliverHours,
                                Boolean reactivationEnabled, Integer reactivationDays,
                                String reactivationCouponCode) {}

    @GetMapping("/api/comida/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PatchMapping("/api/comida/config")
    public ResponseEntity<Object> patch(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        java.time.LocalTime opens = null;
        java.time.LocalTime closes = null;
        try {
            if (req.opensAt() != null && !req.opensAt().isBlank()) opens = java.time.LocalTime.parse(req.opensAt());
            if (req.closesAt() != null && !req.closesAt().isBlank()) closes = java.time.LocalTime.parse(req.closesAt());
        } catch (java.time.DateTimeException e) {
            return error(400, "Bad Request", "invalid_time");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(),
                req.deliveryFeeCents(), req.minOrderCents(), opens, closes,
                req.autoDeliverHours(),
                req.reactivationEnabled() != null && req.reactivationEnabled(),
                req.reactivationDays() == null ? 30 : req.reactivationDays(),
                req.reactivationCouponCode()));
        } catch (IllegalArgumentException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
