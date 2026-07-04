package com.meada.profiles.otica.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.otica.OticaProfileGuard;
import com.meada.profiles.otica.OticaProfileGuard.WrongProfileException;
import com.meada.profiles.otica.config.OticaConfigService.InvalidHoursException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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
 * Config FUNDIDA do otica (camada 8.12). TENANT + perfil 'otica' only. GET (com fallback aos
 * defaults) + PUT (upsert). Carrega janela/duração do EXAME (FLUXO A) e mínimo/lead da ENCOMENDA
 * (FLUXO B). Clone do {@code DentalClinicConfigController} + os campos do fluxo B.
 */
@RestController
public class OticaConfigController {

    private final OticaConfigService service;
    private final OticaProfileGuard profileGuard;

    public OticaConfigController(OticaConfigService service, OticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do PUT. opensAt/closesAt em "HH:MM" (24h). */
    public record ConfigRequest(
        @NotBlank String opensAt,
        @NotBlank String closesAt,
        @Min(15) @Max(240) int examDurationMinutes,
        @Min(0) int minOrderCents,
        @Min(0) int leadTimeDaysDefault,
        Boolean examReminderEnabled,
        Boolean pickupFollowupEnabled,
        Integer pickupFollowupDays) {}

    @GetMapping("/api/otica/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/otica/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOtica(user);
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
                req.examDurationMinutes(), req.minOrderCents(), req.leadTimeDaysDefault(),
                req.examReminderEnabled() == null || req.examReminderEnabled(),
                req.pickupFollowupEnabled() == null || req.pickupFollowupEnabled(),
                req.pickupFollowupDays() == null ? 3 : req.pickupFollowupDays()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
