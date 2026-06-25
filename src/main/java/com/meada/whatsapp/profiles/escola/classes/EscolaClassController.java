package com.meada.whatsapp.profiles.escola.classes;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.escola.classes.EscolaClassService.ClassInUseException;
import com.meada.whatsapp.profiles.escola.classes.EscolaClassService.ClassNotFoundException;
import com.meada.whatsapp.profiles.escola.classes.EscolaClassService.InvalidShiftException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turmas do tenant escola (camada 8.19). TENANT + perfil 'escola' only. O item da lista inclui
 * {@code remainingSlots} (vagas restantes = capacity - matrículas ativas/suspensas), pra UI e pra IA.
 */
@RestController
public class EscolaClassController {

    private final EscolaClassService service;
    private final EscolaProfileGuard profileGuard;

    public EscolaClassController(EscolaClassService service, EscolaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Item enriquecido com vagas restantes (a entidade + o computado). */
    private Map<String, Object> withSlots(EscolaClass c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("name", c.name());
        m.put("grade", c.grade());
        m.put("shift", c.shift());
        m.put("capacity", c.capacity());
        m.put("monthlyCents", c.monthlyCents());
        m.put("year", c.year());
        m.put("description", c.description());
        m.put("active", c.active());
        m.put("remainingSlots", service.remainingSlots(c));
        m.put("createdAt", c.createdAt().toString());
        m.put("updatedAt", c.updatedAt().toString());
        return m;
    }

    public record CreateClassRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String grade,
        @NotBlank String shift,
        @Min(1) @Max(200) int capacity,
        @PositiveOrZero int monthlyCents,
        Integer year,
        String description) {}

    public record UpdateClassRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String grade,
        String shift,
        @Min(1) @Max(200) Integer capacity,
        @PositiveOrZero Integer monthlyCents,
        Integer year,
        String description,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/escola/classes")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive,
            @RequestParam(required = false) String shift) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        List<Map<String, Object>> items = service.list(companyId, onlyActive, shift)
            .stream().map(this::withSlots).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/api/escola/classes/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(c -> ResponseEntity.ok(withSlots(c)))
            .orElseGet(() -> error(404, "Not Found", "class_not_found"));
    }

    @PostMapping("/api/escola/classes")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateClassRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            EscolaClass created = service.create(companyId, user.userId(), req.name(), req.grade(),
                req.shift(), req.capacity(), req.monthlyCents(), req.year(), req.description());
            return ResponseEntity.status(201).body(withSlots(created));
        } catch (InvalidShiftException e) {
            return error(400, "Bad Request", "invalid_shift");
        }
    }

    @PatchMapping("/api/escola/classes/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClassRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            EscolaClass updated = service.update(companyId, user.userId(), id, req.name(), req.grade(),
                req.shift(), req.capacity(), req.monthlyCents(), req.year(), req.description(), req.active());
            return ResponseEntity.ok(withSlots(updated));
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        } catch (InvalidShiftException e) {
            return error(400, "Bad Request", "invalid_shift");
        }
    }

    @PatchMapping("/api/escola/classes/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(withSlots(service.toggle(companyId, user.userId(), id, req.active())));
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        }
    }

    @DeleteMapping("/api/escola/classes/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
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
