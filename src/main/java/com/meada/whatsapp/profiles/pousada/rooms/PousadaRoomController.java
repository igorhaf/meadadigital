package com.meada.whatsapp.profiles.pousada.rooms;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.pousada.PousadaProfileGuard;
import com.meada.whatsapp.profiles.pousada.PousadaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.pousada.rooms.PousadaRoomService.RoomInUseException;
import com.meada.whatsapp.profiles.pousada.rooms.PousadaRoomService.RoomNotFoundException;
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

import java.util.Map;
import java.util.UUID;

/**
 * Quartos do tenant pousada (camada 7.6). TENANT + perfil 'pousada' only.
 */
@RestController
public class PousadaRoomController {

    private final PousadaRoomService service;
    private final PousadaProfileGuard profileGuard;

    public PousadaRoomController(PousadaRoomService service, PousadaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRoomRequest(
        @NotBlank @Size(max = 200) String name,
        @Min(1) @Max(20) int capacity,
        @PositiveOrZero int nightlyRateCents,
        String description,
        String notes) {}

    public record UpdateRoomRequest(
        @Size(max = 200) String name,
        @Min(1) @Max(20) Integer capacity,
        @PositiveOrZero Integer nightlyRateCents,
        String description,
        String notes,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/pousada/rooms")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/pousada/rooms/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "room_not_found"));
    }

    @PostMapping("/api/pousada/rooms")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRoomRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        PousadaRoom created = service.create(companyId, user.userId(), req.name(), req.capacity(),
            req.nightlyRateCents(), req.description(), req.notes());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/pousada/rooms/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.capacity(), req.nightlyRateCents(), req.description(), req.notes(), req.active()));
        } catch (RoomNotFoundException e) {
            return error(404, "Not Found", "room_not_found");
        }
    }

    @PatchMapping("/api/pousada/rooms/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (RoomNotFoundException e) {
            return error(404, "Not Found", "room_not_found");
        }
    }

    @DeleteMapping("/api/pousada/rooms/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (RoomNotFoundException e) {
            return error(404, "Not Found", "room_not_found");
        } catch (RoomInUseException e) {
            return error(409, "Conflict", "room_in_use");
        }
    }
}
