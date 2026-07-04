package com.meada.profiles.fotografia.packages;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.fotografia.FotografiaProfileGuard;
import com.meada.profiles.fotografia.FotografiaProfileGuard.WrongProfileException;
import com.meada.profiles.fotografia.packages.FotografiaPackageService.InvalidDeliveryDaysException;
import com.meada.profiles.fotografia.packages.FotografiaPackageService.InvalidDurationException;
import com.meada.profiles.fotografia.packages.FotografiaPackageService.InvalidPriceException;
import com.meada.profiles.fotografia.packages.FotografiaPackageService.PackageInUseException;
import com.meada.profiles.fotografia.packages.FotografiaPackageService.PackageNotFoundException;
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
 * Pacotes do tenant fotografia (camada 8.16). TENANT + perfil 'fotografia' only. Duração 15..1440 →
 * 400 invalid_duration; preço/prazo >= 0. DELETE em uso → 409 package_in_use. Espelho do
 * DermatologiaProcedureTypeController.
 */
@RestController
public class FotografiaPackageController {

    private final FotografiaPackageService service;
    private final FotografiaProfileGuard profileGuard;

    public FotografiaPackageController(FotografiaPackageService service, FotografiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String name,
        String category,
        @NotNull Integer durationMinutes,
        @NotNull Integer priceCents,
        Integer deliveryDays,
        String notes,
        Boolean suggestible) {}

    public record UpdateRequest(
        @Size(max = 200) String name,
        String category,
        Integer durationMinutes,
        Integer priceCents,
        Integer deliveryDays,
        String notes,
        Boolean active,
        Boolean suggestible) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/fotografia/packages")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/fotografia/packages/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "package_not_found"));
    }

    @PostMapping("/api/fotografia/packages")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            int deliveryDays = req.deliveryDays() == null ? 0 : req.deliveryDays();
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.category(), req.durationMinutes(), req.priceCents(), deliveryDays, req.notes(),
                req.suggestible() != null && req.suggestible()));
        } catch (InvalidDurationException e) {
            return error(400, "Bad Request", "invalid_duration");
        } catch (InvalidPriceException e) {
            return error(400, "Bad Request", "invalid_price");
        } catch (InvalidDeliveryDaysException e) {
            return error(400, "Bad Request", "invalid_delivery_days");
        }
    }

    @PatchMapping("/api/fotografia/packages/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), req.category(),
                req.durationMinutes(), req.priceCents(), req.deliveryDays(), req.notes(), req.active(),
                req.suggestible()));
        } catch (PackageNotFoundException e) {
            return error(404, "Not Found", "package_not_found");
        } catch (InvalidDurationException e) {
            return error(400, "Bad Request", "invalid_duration");
        } catch (InvalidPriceException e) {
            return error(400, "Bad Request", "invalid_price");
        } catch (InvalidDeliveryDaysException e) {
            return error(400, "Bad Request", "invalid_delivery_days");
        }
    }

    @PatchMapping("/api/fotografia/packages/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (PackageNotFoundException e) {
            return error(404, "Not Found", "package_not_found");
        }
    }

    @DeleteMapping("/api/fotografia/packages/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireFotografia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (PackageNotFoundException e) {
            return error(404, "Not Found", "package_not_found");
        } catch (PackageInUseException e) {
            return error(409, "Conflict", "package_in_use");
        }
    }
}
