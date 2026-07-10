package com.meada.profiles.concessionaria.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard;
import com.meada.profiles.concessionaria.ConcessionariaProfileGuard.WrongProfileException;
import com.meada.profiles.concessionaria.config.ConcessionariaConfigService.InvalidHoursException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * Config da concessionaria (camada 8.17). TENANT + perfil 'concessionaria' only. GET (com fallback aos
 * defaults) + PUT (upsert).
 */
@RestController
public class ConcessionariaConfigController {

    private final ConcessionariaConfigService service;
    private final ConcessionariaProfileGuard profileGuard;

    public ConcessionariaConfigController(ConcessionariaConfigService service,
                                          ConcessionariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do PUT. opensAt/closesAt em "HH:MM" (24h). */
    public record ConfigRequest(
        @Size(max = 200) String businessName,
        @Min(15) @Max(240) int durationMinutes,
        @Min(0) int bufferMinutes,
        @NotBlank String opensAt,
        @NotBlank String closesAt,
        String notes,
        Boolean followupEnabled,
        Integer followupDays,
        Boolean testdriveReminderEnabled,
        Boolean autoCompleteEnabled,
        Boolean postSaleEnabled,
        String reviewLink,
        Boolean serviceReminderEnabled,
        Integer serviceReminderMonths) {}

    @GetMapping("/api/concessionaria/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/concessionaria/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireConcessionaria(user);
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
            // toggles ausentes no payload = mantém o default LIGADO (opt-out).
            return ResponseEntity.ok(service.update(
                companyId, user.userId(), req.businessName(), req.durationMinutes(),
                req.bufferMinutes(), opensAt, closesAt, req.notes(),
                req.followupEnabled() == null || req.followupEnabled(),
                req.followupDays() == null ? 3 : req.followupDays(),
                req.testdriveReminderEnabled() == null || req.testdriveReminderEnabled(),
                req.autoCompleteEnabled() == null || req.autoCompleteEnabled(),
                req.postSaleEnabled() == null || req.postSaleEnabled(),
                req.reviewLink(),
                Boolean.TRUE.equals(req.serviceReminderEnabled()),
                req.serviceReminderMonths() == null ? 12 : req.serviceReminderMonths()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
