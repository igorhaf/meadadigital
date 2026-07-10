package com.meada.profiles.estetica.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.estetica.EsteticaProfileGuard;
import com.meada.profiles.estetica.EsteticaProfileGuard.WrongProfileException;
import com.meada.profiles.estetica.config.AestheticConfigService.InvalidHoursException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Config do tenant estetica (camada 8.3). TENANT + perfil 'estetica' only. GET (fallback) + PUT. */
@RestController
public class AestheticConfigController {

    private final AestheticConfigService service;
    private final EsteticaProfileGuard profileGuard;

    public AestheticConfigController(AestheticConfigService service, EsteticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(@NotBlank String opensAt, @NotBlank String closesAt, @Min(5) int slotMinutes,
                                Boolean reminderEnabled, Boolean autoCompleteEnabled,
                                Boolean autoExpireEnabled, Integer packageValidityDays,
                                Boolean renewalEnabled, Integer renewalDays, Integer expiryWarningDays) {}

    @GetMapping("/api/estetica/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/estetica/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalTime opensAt;
        LocalTime closesAt;
        try {
            opensAt = LocalTime.parse(req.opensAt());
            closesAt = LocalTime.parse(req.closesAt());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_time");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), opensAt, closesAt,
                req.slotMinutes(),
                req.reminderEnabled() == null || req.reminderEnabled(),
                req.autoCompleteEnabled() == null || req.autoCompleteEnabled(),
                req.autoExpireEnabled() == null || req.autoExpireEnabled(),
                req.packageValidityDays(),
                req.renewalEnabled() != null && req.renewalEnabled(),
                req.renewalDays() == null ? 30 : req.renewalDays(),
                req.expiryWarningDays() == null ? 7 : req.expiryWarningDays()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
