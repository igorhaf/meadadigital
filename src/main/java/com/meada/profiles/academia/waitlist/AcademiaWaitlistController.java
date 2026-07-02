package com.meada.profiles.academia.waitlist;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.profiles.academia.waitlist.AcademiaWaitlistService.AlreadyWaitingException;
import com.meada.profiles.academia.waitlist.AcademiaWaitlistService.EntryNotFoundException;
import com.meada.profiles.academia.waitlist.AcademiaWaitlistService.InvalidStatusException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
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
 * Lista de espera por aula do tenant academia (migration 74). TENANT + perfil 'academia' only.
 * POST enfileira; GET lista por aula (posição derivada); PATCH muta o status.
 */
@RestController
public class AcademiaWaitlistController {

    private final AcademiaWaitlistService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaWaitlistController(AcademiaWaitlistService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record EnqueueRequest(
        @NotNull UUID classId,
        UUID contactId,
        @NotBlank @Size(max = 200) String studentName,
        @Size(max = 40) String studentPhone) {}

    public record StatusRequest(@NotBlank String status) {}

    @GetMapping("/api/academia/waitlist")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam UUID classId,
            @RequestParam(defaultValue = "false") boolean onlyWaiting) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, classId, onlyWaiting)));
    }

    @PostMapping("/api/academia/waitlist")
    public ResponseEntity<Object> enqueue(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody EnqueueRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            AcademiaWaitlistEntry created = service.enqueue(companyId, user.userId(), req.classId(),
                req.contactId(), req.studentName(), req.studentPhone());
            return ResponseEntity.status(201).body(created);
        } catch (AlreadyWaitingException e) {
            return error(409, "Conflict", "already_waiting");
        }
    }

    @PatchMapping("/api/academia/waitlist/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, user.userId(), id, req.status()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (AlreadyWaitingException e) {
            return error(409, "Conflict", "already_waiting");
        } catch (EntryNotFoundException e) {
            return error(404, "Not Found", "waitlist_entry_not_found");
        }
    }
}
