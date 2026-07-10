package com.meada.profiles.dermatologia.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.dermatologia.DermatologiaProfileGuard;
import com.meada.profiles.dermatologia.DermatologiaProfileGuard.WrongProfileException;
import com.meada.profiles.dermatologia.config.DermatologiaConfigService.InvalidHoursException;
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

/** Config do dermatologia (camada 8.11). TENANT + perfil 'dermatologia' only. GET (fallback) + PUT. */
@RestController
public class DermatologiaConfigController {

    private final DermatologiaConfigService service;
    private final DermatologiaProfileGuard profileGuard;

    public DermatologiaConfigController(DermatologiaConfigService service, DermatologiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(@NotBlank String opensAt, @NotBlank String closesAt, @Min(0) int bufferMinutes,
                                Boolean reminderEnabled, Boolean autoCompleteEnabled,
                                Boolean recallEnabled, Integer recallMonths) {}

    @GetMapping("/api/dermatologia/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/dermatologia/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
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
                req.bufferMinutes(),
                req.reminderEnabled() == null || req.reminderEnabled(),
                req.autoCompleteEnabled() == null || req.autoCompleteEnabled(),
                req.recallEnabled() != null && req.recallEnabled(),
                req.recallMonths() == null ? 6 : req.recallMonths()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
