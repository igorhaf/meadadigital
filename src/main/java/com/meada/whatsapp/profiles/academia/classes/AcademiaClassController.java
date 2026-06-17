package com.meada.whatsapp.profiles.academia.classes;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.academia.AcademiaProfileGuard;
import com.meada.whatsapp.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClassService.ClassInUseException;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClassService.ClassNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aulas do tenant academia (camada 7.7). TENANT + perfil 'academia' only. O item da lista inclui
 * {@code remainingSlots} (vagas restantes = capacity - matrículas ativas), pra UI e pra IA.
 */
@RestController
public class AcademiaClassController {

    private final AcademiaClassService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaClassController(AcademiaClassService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Item enriquecido com vagas restantes (a entidade + o computado). */
    private Map<String, Object> withSlots(AcademiaClass c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("name", c.name());
        m.put("modality", c.modality());
        m.put("dayOfWeek", c.dayOfWeek());
        m.put("startTime", c.startTime().toString());
        m.put("durationMinutes", c.durationMinutes());
        m.put("capacity", c.capacity());
        m.put("instructor", c.instructor());
        m.put("active", c.active());
        m.put("remainingSlots", service.remainingSlots(c));
        m.put("createdAt", c.createdAt().toString());
        m.put("updatedAt", c.updatedAt().toString());
        return m;
    }

    public record CreateClassRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String modality,
        @Min(0) @Max(6) int dayOfWeek,
        @NotBlank String startTime,
        @Min(15) @Max(240) int durationMinutes,
        @Min(1) @Max(100) int capacity,
        String instructor) {}

    public record UpdateClassRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String modality,
        @Min(0) @Max(6) Integer dayOfWeek,
        String startTime,
        @Min(15) @Max(240) Integer durationMinutes,
        @Min(1) @Max(100) Integer capacity,
        String instructor,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/academia/classes")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive,
            @RequestParam(required = false) Integer dayOfWeek) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        List<Map<String, Object>> items = service.list(companyId, onlyActive, dayOfWeek)
            .stream().map(this::withSlots).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/api/academia/classes/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(c -> ResponseEntity.ok(withSlots(c)))
            .orElseGet(() -> error(404, "Not Found", "class_not_found"));
    }

    @PostMapping("/api/academia/classes")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateClassRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalTime start;
        try {
            start = LocalTime.parse(req.startTime());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_time");
        }
        AcademiaClass created = service.create(companyId, user.userId(), req.name(), req.modality(),
            req.dayOfWeek(), start, req.durationMinutes(), req.capacity(), req.instructor());
        return ResponseEntity.status(201).body(withSlots(created));
    }

    @PatchMapping("/api/academia/classes/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalTime start = null;
        if (req.startTime() != null && !req.startTime().isBlank()) {
            try {
                start = LocalTime.parse(req.startTime());
            } catch (DateTimeException e) {
                return error(400, "Bad Request", "invalid_time");
            }
        }
        try {
            AcademiaClass updated = service.update(companyId, user.userId(), id, req.name(), req.modality(),
                req.dayOfWeek(), start, req.durationMinutes(), req.capacity(), req.instructor(), req.active());
            return ResponseEntity.ok(withSlots(updated));
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        }
    }

    @PatchMapping("/api/academia/classes/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(withSlots(service.toggle(companyId, user.userId(), id, req.active())));
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        }
    }

    @DeleteMapping("/api/academia/classes/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        } catch (ClassInUseException e) {
            return error(409, "Conflict", "class_in_use");
        }
    }
}
