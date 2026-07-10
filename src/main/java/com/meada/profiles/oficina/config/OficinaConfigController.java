package com.meada.profiles.oficina.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.oficina.OficinaProfileGuard;
import com.meada.profiles.oficina.OficinaProfileGuard.WrongProfileException;
import com.meada.profiles.oficina.config.OficinaConfigService.InvalidHoursException;
import jakarta.validation.Valid;
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

/** Config da oficina (camada 7.9). TENANT + perfil 'oficina' only. GET (fallback) + PUT. */
@RestController
public class OficinaConfigController {

    private final OficinaConfigService service;
    private final OficinaProfileGuard profileGuard;

    public OficinaConfigController(OficinaConfigService service, OficinaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(@NotBlank String opensAt, @NotBlank String closesAt,
        Boolean returnReminderEnabled,
        Integer returnReminderDays) {}

    @GetMapping("/api/oficina/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/oficina/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
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
                req.returnReminderEnabled() == null || req.returnReminderEnabled(),
                req.returnReminderDays() == null ? 180 : req.returnReminderDays()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
