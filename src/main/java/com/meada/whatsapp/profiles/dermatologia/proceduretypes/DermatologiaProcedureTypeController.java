package com.meada.whatsapp.profiles.dermatologia.proceduretypes;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaProfileGuard;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeService.InvalidDurationException;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeService.ProcedureTypeInUseException;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeService.ProcedureTypeNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

import java.util.Map;
import java.util.UUID;

/**
 * Tipos de atendimento do tenant dermatologia (camada 8.11, ESCAPADA). TENANT + perfil
 * 'dermatologia' only. prep_instructions nullable; duração 5..480 → 400 invalid_duration.
 */
@RestController
public class DermatologiaProcedureTypeController {

    private final DermatologiaProcedureTypeService service;
    private final DermatologiaProfileGuard profileGuard;

    public DermatologiaProcedureTypeController(DermatologiaProcedureTypeService service, DermatologiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Integer durationMinutes,
        String prepInstructions,
        String notes) {}

    public record UpdateRequest(
        @Size(max = 120) String name,
        Integer durationMinutes,
        String prepInstructions,
        Boolean clearPrep,
        String notes,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/dermatologia/procedure-types")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/dermatologia/procedure-types/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "procedure_type_not_found"));
    }

    @PostMapping("/api/dermatologia/procedure-types")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.durationMinutes(), req.prepInstructions(), req.notes()));
        } catch (InvalidDurationException e) {
            return error(400, "Bad Request", "invalid_duration");
        }
    }

    @PatchMapping("/api/dermatologia/procedure-types/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean prepProvided = req.prepInstructions() != null || Boolean.TRUE.equals(req.clearPrep());
        String prep = Boolean.TRUE.equals(req.clearPrep()) ? null : req.prepInstructions();
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), req.durationMinutes(),
                prep, prepProvided, req.notes(), req.active()));
        } catch (ProcedureTypeNotFoundException e) {
            return error(404, "Not Found", "procedure_type_not_found");
        } catch (InvalidDurationException e) {
            return error(400, "Bad Request", "invalid_duration");
        }
    }

    @PatchMapping("/api/dermatologia/procedure-types/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (ProcedureTypeNotFoundException e) {
            return error(404, "Not Found", "procedure_type_not_found");
        }
    }

    @DeleteMapping("/api/dermatologia/procedure-types/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ProcedureTypeNotFoundException e) {
            return error(404, "Not Found", "procedure_type_not_found");
        } catch (ProcedureTypeInUseException e) {
            return error(409, "Conflict", "procedure_type_in_use");
        }
    }
}
