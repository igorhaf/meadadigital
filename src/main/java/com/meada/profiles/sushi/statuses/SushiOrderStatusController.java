package com.meada.profiles.sushi.statuses;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.sushi.SushiProfileGuard;
import com.meada.profiles.sushi.SushiProfileGuard.WrongProfileException;
import com.meada.profiles.sushi.statuses.SushiOrderStatusService.DuplicateStatusException;
import com.meada.profiles.sushi.statuses.SushiOrderStatusService.InitialStatusUndeletableException;
import com.meada.profiles.sushi.statuses.SushiOrderStatusService.InvalidStatusNameException;
import com.meada.profiles.sushi.statuses.SushiOrderStatusService.StatusInUseException;
import com.meada.profiles.sushi.statuses.SushiOrderStatusService.StatusNotFoundException;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Estados do pedido do tenant sushi + notificações editáveis (camada 7.1 / sushi funcional).
 * TENANT + perfil 'sushi' only. As notificações são os campos {@code notifyEnabled}/{@code notifyText}
 * editáveis no PATCH (não há controller separado). Sob {@code /api/sushi/**} (já autenticado).
 */
@RestController
public class SushiOrderStatusController {

    private final SushiOrderStatusService service;
    private final SushiProfileGuard profileGuard;

    public SushiOrderStatusController(SushiOrderStatusService service, SushiProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 60) String name,
        Integer sortOrder,
        Boolean isInitial,
        Boolean isTerminal,
        Boolean notifyEnabled,
        String notifyText,
        String color) {}

    public record UpdateRequest(
        @Size(max = 60) String name,
        Integer sortOrder,
        Boolean isInitial,
        Boolean isTerminal,
        Boolean notifyEnabled,
        String notifyText,
        Boolean clearNotifyText,
        String color,
        Boolean clearColor) {}

    @GetMapping("/api/sushi/order-statuses")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId)));
    }

    @PostMapping("/api/sushi/order-statuses")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.sortOrder(), req.isInitial(), req.isTerminal(), req.notifyEnabled(),
                req.notifyText(), req.color()));
        } catch (InvalidStatusNameException e) {
            return error(400, "Bad Request", "invalid_status_name");
        } catch (DuplicateStatusException e) {
            return error(409, "Conflict", "duplicate_status");
        }
    }

    @PatchMapping("/api/sushi/order-statuses/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean notifyTextProvided = req.notifyText() != null || Boolean.TRUE.equals(req.clearNotifyText());
        String notifyText = Boolean.TRUE.equals(req.clearNotifyText()) ? null : req.notifyText();
        boolean colorProvided = req.color() != null || Boolean.TRUE.equals(req.clearColor());
        String color = Boolean.TRUE.equals(req.clearColor()) ? null : req.color();
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.sortOrder(), req.isInitial(), req.isTerminal(), req.notifyEnabled(),
                notifyText, notifyTextProvided, color, colorProvided));
        } catch (InvalidStatusNameException e) {
            return error(400, "Bad Request", "invalid_status_name");
        } catch (StatusNotFoundException e) {
            return error(404, "Not Found", "status_not_found");
        } catch (DuplicateStatusException e) {
            return error(409, "Conflict", "duplicate_status");
        }
    }

    @DeleteMapping("/api/sushi/order-statuses/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (StatusNotFoundException e) {
            return error(404, "Not Found", "status_not_found");
        } catch (InitialStatusUndeletableException e) {
            return error(409, "Conflict", "initial_status_undeletable");
        } catch (StatusInUseException e) {
            return error(409, "Conflict", "status_in_use");
        }
    }
}
