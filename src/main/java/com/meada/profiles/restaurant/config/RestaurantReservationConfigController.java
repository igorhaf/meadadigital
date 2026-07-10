package com.meada.profiles.restaurant.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.restaurant.RestaurantProfileGuard;
import com.meada.profiles.restaurant.RestaurantProfileGuard.WrongProfileException;
import com.meada.profiles.restaurant.config.RestaurantReservationConfigService.InvalidHoursException;
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
 * Config de reservas do tenant restaurant (camada 7.3). TENANT + perfil 'restaurant' only.
 * GET (com fallback aos defaults) + PUT (upsert).
 */
@RestController
public class RestaurantReservationConfigController {

    private final RestaurantReservationConfigService service;
    private final RestaurantProfileGuard profileGuard;

    public RestaurantReservationConfigController(RestaurantReservationConfigService service,
                                                 RestaurantProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do PUT. opensAt/closesAt em "HH:MM" (24h). */
    public record ConfigRequest(
        @Min(30) @Max(600) int durationMinutes,
        @Min(0) int bufferMinutes,
        @NotBlank String opensAt,
        @NotBlank String closesAt,
        Boolean reminderEnabled,
        Boolean autoCompleteEnabled) {}

    @GetMapping("/api/restaurant/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/restaurant/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireRestaurant(user);
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
            return ResponseEntity.ok(service.update(
                companyId, user.userId(), req.durationMinutes(), req.bufferMinutes(), opensAt, closesAt,
                req.reminderEnabled() == null || req.reminderEnabled(),
                req.autoCompleteEnabled() == null || req.autoCompleteEnabled()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
