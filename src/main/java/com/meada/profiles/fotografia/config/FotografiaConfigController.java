package com.meada.profiles.fotografia.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.fotografia.FotografiaProfileGuard;
import com.meada.profiles.fotografia.FotografiaProfileGuard.WrongProfileException;
import com.meada.profiles.fotografia.config.FotografiaConfigService.InvalidHoursException;
import com.meada.profiles.fotografia.config.FotografiaConfigService.InvalidSlotException;
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

/** Config do fotografia (camada 8.16). TENANT + perfil 'fotografia' only. GET (fallback) + PUT. Espelho do DermatologiaConfigController. */
@RestController
public class FotografiaConfigController {

    private final FotografiaConfigService service;
    private final FotografiaProfileGuard profileGuard;

    public FotografiaConfigController(FotografiaConfigService service, FotografiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(@NotBlank String opensAt, @NotBlank String closesAt, @Min(5) int slotMinutes,
                                Boolean reminderEnabled, Boolean autoCompleteEnabled,
                                Boolean autoDeliverEnabled, Boolean postDeliveryUpsellEnabled,
                                Integer cancellationPolicyHours) {}

    @GetMapping("/api/fotografia/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/fotografia/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
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
                req.autoDeliverEnabled() == null || req.autoDeliverEnabled(),
                req.postDeliveryUpsellEnabled() == null || req.postDeliveryUpsellEnabled(),
                req.cancellationPolicyHours()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        } catch (InvalidSlotException e) {
            return error(400, "Bad Request", "invalid_slot");
        }
    }
}
