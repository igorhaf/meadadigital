package com.meada.whatsapp.profiles.legal.cases;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.legal.LegalProfileGuard;
import com.meada.whatsapp.profiles.legal.LegalProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.DuplicateCnjException;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.InvalidCnjException;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.InvalidStatusException;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.LegalCaseNotFoundException;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.LegalClientNotFoundException;
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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * Processos do tenant legal (camada 7.2). TENANT + perfil 'legal' only. Sob /api/legal/cases.
 */
@RestController
public class LegalCaseController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LegalCaseService service;
    private final LegalProfileGuard profileGuard;

    public LegalCaseController(LegalCaseService service, LegalProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateCaseRequest(
        @NotNull UUID legalClientId,
        @NotBlank String cnjNumber,
        @NotBlank @Size(max = 200) String title,
        String description, String court, String forum, String subject) {}

    public record UpdateCaseRequest(
        @Size(max = 200) String title,
        String description, String court, String forum, String subject) {}

    public record StatusRequest(String newStatus) {}

    public record AddUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        String body, String occurredAt) {}

    @GetMapping("/api/legal/cases")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, search, size, safePage * size),
            "total", service.count(companyId, status, search), "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/legal/cases/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "case_not_found"));
    }

    @PostMapping("/api/legal/cases")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateCaseRequest req) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(),
                req.legalClientId(), req.cnjNumber(), req.title(), req.description(),
                req.court(), req.forum(), req.subject()));
        } catch (InvalidCnjException e) {
            return error(400, "Bad Request", "invalid_cnj");
        } catch (DuplicateCnjException e) {
            return error(409, "Conflict", "duplicate_cnj");
        } catch (LegalClientNotFoundException e) {
            return error(404, "Not Found", "client_not_found");
        }
    }

    @PatchMapping("/api/legal/cases/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateCaseRequest req) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.title(), req.description(), req.court(), req.forum(), req.subject()));
        } catch (LegalCaseNotFoundException e) {
            return error(404, "Not Found", "case_not_found");
        }
    }

    @PatchMapping("/api/legal/cases/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, user.userId(), id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (LegalCaseNotFoundException e) {
            return error(404, "Not Found", "case_not_found");
        }
    }

    @PostMapping("/api/legal/cases/{id}/updates")
    public ResponseEntity<Object> addUpdate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody AddUpdateRequest req) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        Instant occurredAt;
        try {
            occurredAt = (req.occurredAt() == null || req.occurredAt().isBlank())
                ? Instant.now() : Instant.parse(req.occurredAt());
        } catch (DateTimeParseException e) {
            return error(400, "Bad Request", "invalid_occurred_at");
        }
        try {
            return ResponseEntity.status(201).body(
                service.addUpdate(companyId, user.userId(), id, req.title(), req.body(), occurredAt));
        } catch (LegalCaseNotFoundException e) {
            return error(404, "Not Found", "case_not_found");
        }
    }

    @DeleteMapping("/api/legal/cases/{id}/updates/{updateId}")
    public ResponseEntity<Object> deleteUpdate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID updateId) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        try {
            service.removeUpdate(companyId, user.userId(), id, updateId);
            return ResponseEntity.noContent().build();
        } catch (LegalCaseNotFoundException e) {
            return error(404, "Not Found", "case_or_update_not_found");
        }
    }
}
