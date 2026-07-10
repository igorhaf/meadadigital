package com.meada.profiles.cursos.config;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.cursos.CursosProfileGuard;
import com.meada.profiles.cursos.CursosProfileGuard.WrongProfileException;
import com.meada.profiles.cursos.config.CursosConfigService.InvalidHoursException;
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

/**
 * Config do tenant cursos (camada 8.20 / perfil cursos). TENANT + perfil 'cursos' only. GET (fallback
 * defaults) + PUT. Análogo ao AcademiaConfigController (camada 7.7) com o campo extra {@code notes}.
 */
@RestController
public class CursosConfigController {

    private final CursosConfigService service;
    private final CursosProfileGuard profileGuard;

    public CursosConfigController(CursosConfigService service, CursosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record ConfigRequest(
        @NotBlank String opensAt,
        @NotBlank String closesAt,
        String notes,
        Boolean nudgeEnabled,
        Integer nudgeDays,
        String certificateBaseUrl) {}

    @GetMapping("/api/cursos/config")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(service.get(companyId));
    }

    @PutMapping("/api/cursos/config")
    public ResponseEntity<Object> put(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ConfigRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCursos(user);
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
                req.notes(),
                req.nudgeEnabled() == null || req.nudgeEnabled(),
                req.nudgeDays() == null ? 7 : req.nudgeDays(),
                req.certificateBaseUrl()));
        } catch (InvalidHoursException e) {
            return error(400, "Bad Request", "invalid_hours");
        }
    }
}
