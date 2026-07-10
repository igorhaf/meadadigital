package com.meada.profiles.barbearia.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.barbearia.BarberProfileGuard;
import com.meada.profiles.barbearia.BarberProfileGuard.WrongProfileException;
import com.meada.profiles.barbearia.config.BarberConfigService.InvalidHoursException;
import com.meada.profiles.barbearia.config.BarberConfigService.InvalidSlotException;
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

/**
 * Config da barbearia (camada 8.1). TENANT + perfil 'barbearia' only. GET (fallback defaults) + PUT
 * (upsert). Espelho de SalonConfigController + slot_minutes e queue_enabled.
 */
@RestController
public class BarberConfigController {

    private final BarberConfigService service;
    private final BarberProfileGuard profileGuard;

    public BarberConfigController(BarberConfigService service, BarberProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(
        @NotBlank String opensAt,
        @NotBlank String closesAt,
        @Min(1) int slotMinutes,
        boolean queueEnabled,
        Boolean reminderEnabled,
        Boolean autoCompleteEnabled,
        Boolean upsellEnabled,
        Boolean reactivationEnabled,
        Integer reactivationDays,
        String reactivationCouponCode,
        Boolean postReviewEnabled,
        String reviewLink,
        Integer reviewCooldownDays) {}

    @GetMapping("/api/barbearia/config")
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

    @PutMapping("/api/barbearia/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
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
            // ausentes no payload → defaults (lembrete/auto-transição ON, upsell OFF — opt-in explícito).
            return ResponseEntity.ok(service.update(companyId, user.userId(), opensAt, closesAt,
                req.slotMinutes(), req.queueEnabled(),
                req.reminderEnabled() == null || req.reminderEnabled(),
                req.autoCompleteEnabled() == null || req.autoCompleteEnabled(),
                Boolean.TRUE.equals(req.upsellEnabled()),
                Boolean.TRUE.equals(req.reactivationEnabled()),
                req.reactivationDays() == null ? 45 : req.reactivationDays(),
                req.reactivationCouponCode(),
                Boolean.TRUE.equals(req.postReviewEnabled()),
                req.reviewLink(),
                req.reviewCooldownDays() == null ? 90 : req.reviewCooldownDays()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        } catch (InvalidSlotException e) {
            return error(400, "Bad Request", "invalid_slot");
        }
    }
}
