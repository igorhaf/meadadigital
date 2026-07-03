package com.meada.profiles.comida.zones;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.comida.ComidaProfileGuard;
import com.meada.profiles.comida.ComidaProfileGuard.WrongProfileException;
import com.meada.profiles.comida.zones.ComidaDeliveryZoneService.DuplicateZoneException;
import com.meada.profiles.comida.zones.ComidaDeliveryZoneService.InvalidZoneException;
import com.meada.profiles.comida.zones.ComidaDeliveryZoneService.ZoneNotFoundException;
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
 * Zonas de entrega do tenant comida (onda 1, backlog #8). TENANT + perfil 'comida' only. CRUD; a
 * resolução da taxa acontece na criação do pedido (zona da tag; fallback = taxa flat da config).
 */
@RestController
public class ComidaDeliveryZoneController {

    private final ComidaDeliveryZoneService service;
    private final ComidaProfileGuard profileGuard;

    public ComidaDeliveryZoneController(ComidaDeliveryZoneService service, ComidaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull Integer feeCents,
        Boolean active) {}

    public record UpdateRequest(@Size(max = 120) String name, Integer feeCents, Boolean active) {}

    @GetMapping("/api/comida/zones")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @PostMapping("/api/comida/zones")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.feeCents(), req.active()));
        } catch (InvalidZoneException e) {
            return error(400, "Bad Request", "invalid_zone");
        } catch (DuplicateZoneException e) {
            return error(409, "Conflict", "duplicate_zone");
        }
    }

    @PatchMapping("/api/comida/zones/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.feeCents(), req.active()));
        } catch (InvalidZoneException e) {
            return error(400, "Bad Request", "invalid_zone");
        } catch (ZoneNotFoundException e) {
            return error(404, "Not Found", "zone_not_found");
        } catch (DuplicateZoneException e) {
            return error(409, "Conflict", "duplicate_zone");
        }
    }

    @DeleteMapping("/api/comida/zones/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ZoneNotFoundException e) {
            return error(404, "Not Found", "zone_not_found");
        }
    }
}
