package com.meada.whatsapp.profiles.estetica.procedures;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.estetica.procedures.AestheticProcedureService.ProcedureInUseException;
import com.meada.whatsapp.profiles.estetica.procedures.AestheticProcedureService.ProcedureNotFoundException;
import jakarta.validation.Valid;
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

import java.util.Map;
import java.util.UUID;

/** Procedimentos do tenant estetica (camada 8.3). TENANT + perfil 'estetica' only. */
@RestController
public class AestheticProcedureController {

    private final AestheticProcedureService service;
    private final EsteticaProfileGuard profileGuard;

    public AestheticProcedureController(AestheticProcedureService service, EsteticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String name,
        String category,
        @Min(15) int durationMinutes,
        @Min(0) int unitPriceCents,
        String notes) {}

    public record UpdateRequest(
        @Size(max = 200) String name,
        String category,
        Integer durationMinutes,
        Integer unitPriceCents,
        String notes,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/estetica/procedures")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/estetica/procedures/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "procedure_not_found"));
    }

    @PostMapping("/api/estetica/procedures")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
            req.category(), req.durationMinutes(), req.unitPriceCents(), req.notes()));
    }

    @PatchMapping("/api/estetica/procedures/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), req.category(),
                req.durationMinutes(), req.unitPriceCents(), req.notes(), req.active()));
        } catch (ProcedureNotFoundException e) {
            return error(404, "Not Found", "procedure_not_found");
        }
    }

    @PatchMapping("/api/estetica/procedures/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (ProcedureNotFoundException e) {
            return error(404, "Not Found", "procedure_not_found");
        }
    }

    @DeleteMapping("/api/estetica/procedures/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ProcedureNotFoundException e) {
            return error(404, "Not Found", "procedure_not_found");
        } catch (ProcedureInUseException e) {
            return error(409, "Conflict", "procedure_in_use");
        }
    }
}
