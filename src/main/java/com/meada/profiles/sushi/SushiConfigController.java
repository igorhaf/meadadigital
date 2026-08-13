package com.meada.profiles.sushi;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.sushi.SushiProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant sushi (camada 7.1 / sushi funcional): taxa de entrega + pedido mínimo +
 * {@code schedulingEnabled}. TENANT + perfil 'sushi' only. GET (fallback ZERO) + PATCH (upsert).
 * Sob {@code /api/sushi/config}.
 */
@RestController
public class SushiConfigController {

    private final SushiConfigService service;
    private final SushiProfileGuard profileGuard;

    public SushiConfigController(SushiConfigService service, SushiProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(int deliveryFeeCents, int minOrderCents, boolean schedulingEnabled,
                                Boolean upsellEnabled, Boolean reactivationEnabled,
                                Integer reactivationDays, String reactivationCouponCode) {}

    @GetMapping("/api/sushi/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PatchMapping("/api/sushi/config")
    public ResponseEntity<Object> patch(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.update(companyId, user.userId(),
            req.deliveryFeeCents(), req.minOrderCents(), req.schedulingEnabled(),
            req.upsellEnabled() == null || req.upsellEnabled(),
            Boolean.TRUE.equals(req.reactivationEnabled()),
            req.reactivationDays() == null ? 21 : req.reactivationDays(),
            req.reactivationCouponCode()));
    }
}
