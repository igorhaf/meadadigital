package com.meada.profiles.lavanderia.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.lavanderia.LavanderiaProfileGuard;
import com.meada.profiles.lavanderia.LavanderiaProfileGuard.WrongProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Config do tenant lavanderia (camada 8.10): taxa de entrega + pedido mínimo + turnaround default.
 * TENANT + perfil 'lavanderia' only. GET (fallback) + PUT (upsert). Sob {@code /api/lavanderia/config}.
 */
@RestController
public class LavanderiaConfigController {

    private final LavanderiaConfigService service;
    private final LavanderiaProfileGuard profileGuard;

    public LavanderiaConfigController(LavanderiaConfigService service, LavanderiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(int deliveryFeeCents, int minOrderCents, int turnaroundDaysDefault,
                                Boolean expressEnabled, Integer expressSurchargePct,
                                Integer expressTurnaroundDays, Boolean collectReminderEnabled,
                                Boolean readyReminderEnabled, Integer readyReminderDays,
                                Boolean reactivationEnabled, Integer reactivationDays,
                                String reactivationCouponCode) {}

    @GetMapping("/api/lavanderia/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/lavanderia/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLavanderia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.update(companyId, user.userId(),
            req.deliveryFeeCents(), req.minOrderCents(), req.turnaroundDaysDefault(),
            req.expressEnabled() == null || req.expressEnabled(),
            req.expressSurchargePct() == null ? 50 : req.expressSurchargePct(),
            req.expressTurnaroundDays() == null ? 1 : req.expressTurnaroundDays(),
            req.collectReminderEnabled() == null || req.collectReminderEnabled(),
            req.readyReminderEnabled() == null || req.readyReminderEnabled(),
            req.readyReminderDays() == null ? 2 : req.readyReminderDays(),
            req.reactivationEnabled() != null && req.reactivationEnabled(),
            req.reactivationDays() == null ? 30 : req.reactivationDays(),
            req.reactivationCouponCode()));
    }
}
