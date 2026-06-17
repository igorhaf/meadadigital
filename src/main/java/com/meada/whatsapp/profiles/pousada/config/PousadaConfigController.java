package com.meada.whatsapp.profiles.pousada.config;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.pousada.PousadaProfileGuard;
import com.meada.whatsapp.profiles.pousada.PousadaProfileGuard.WrongProfileException;
import jakarta.validation.Valid;
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
 * Config da pousada (camada 7.6). TENANT + perfil 'pousada' only. GET (fallback defaults) + PUT.
 */
@RestController
public class PousadaConfigController {

    private final PousadaConfigService service;
    private final PousadaProfileGuard profileGuard;

    public PousadaConfigController(PousadaConfigService service, PousadaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(
        @NotBlank String checkInTime,
        @NotBlank String checkOutTime,
        @Size(max = 2000) String cancellationPolicy) {}

    @GetMapping("/api/pousada/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/pousada/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalTime checkIn;
        LocalTime checkOut;
        try {
            checkIn = LocalTime.parse(req.checkInTime());
            checkOut = LocalTime.parse(req.checkOutTime());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_time");
        }
        String policy = req.cancellationPolicy() == null || req.cancellationPolicy().isBlank()
            ? null : req.cancellationPolicy();
        return ResponseEntity.ok(service.update(companyId, user.userId(), checkIn, checkOut, policy));
    }
}
