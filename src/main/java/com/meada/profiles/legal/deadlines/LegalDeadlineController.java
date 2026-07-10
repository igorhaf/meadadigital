package com.meada.profiles.legal.deadlines;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.legal.LegalProfileGuard;
import com.meada.profiles.legal.LegalProfileGuard.WrongProfileException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Prazos/audiências do processo (onda Legal 1, backlog #1). TENANT + perfil 'legal'. CRUD +
 * mudança de status (pendente|cumprido|perdido — gestão do advogado, sem máquina rígida).
 */
@RestController
public class LegalDeadlineController {

    private static final Set<String> KINDS = Set.of("prazo", "audiencia");
    private static final Set<String> STATUSES = Set.of("pendente", "cumprido", "perdido");

    private final LegalDeadlineRepository repository;
    private final LegalProfileGuard profileGuard;
    private final com.meada.common.audit.AuditLogger auditLogger;

    public LegalDeadlineController(LegalDeadlineRepository repository, LegalProfileGuard profileGuard,
                                   com.meada.common.audit.AuditLogger auditLogger) {
        this.repository = repository;
        this.profileGuard = profileGuard;
        this.auditLogger = auditLogger;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(@NotBlank String caseId, @NotBlank String kind, @NotBlank String title,
                                @NotBlank String dueDate, String dueTime, String location, String notes) {}

    public record UpdateRequest(String kind, String title, String dueDate, String dueTime,
                                Boolean clearDueTime, String location, String status, String notes) {}

    @GetMapping("/api/legal/deadlines")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID caseId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLegal(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            LocalDate f = from == null || from.isBlank() ? null : LocalDate.parse(from);
            LocalDate t = to == null || to.isBlank() ? null : LocalDate.parse(to);
            return ResponseEntity.ok(Map.of("items", repository.listByCompany(companyId, status, caseId, f, t)));
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
    }

    @PostMapping("/api/legal/deadlines")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLegal(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (!KINDS.contains(req.kind())) {
            return error(400, "Bad Request", "invalid_kind");
        }
        try {
            LocalDate dueDate = LocalDate.parse(req.dueDate());
            LocalTime dueTime = req.dueTime() == null || req.dueTime().isBlank()
                ? null : LocalTime.parse(req.dueTime());
            LegalDeadline created = repository.insert(companyId, UUID.fromString(req.caseId()),
                req.kind(), req.title(), dueDate, dueTime, req.location(), req.notes());
            auditLogger.log(companyId, user.userId(), "legal_deadline_created", "legal_deadline",
                created.id(), Map.of("kind", req.kind()));
            return ResponseEntity.status(201).body(created);
        } catch (DateTimeException | IllegalArgumentException e) {
            return error(400, "Bad Request", "invalid_date");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return error(404, "Not Found", "case_not_found");
        }
    }

    @PatchMapping("/api/legal/deadlines/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLegal(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (req.kind() != null && !req.kind().isBlank() && !KINDS.contains(req.kind())) {
            return error(400, "Bad Request", "invalid_kind");
        }
        if (req.status() != null && !req.status().isBlank() && !STATUSES.contains(req.status())) {
            return error(400, "Bad Request", "invalid_status");
        }
        try {
            LocalDate dueDate = req.dueDate() == null || req.dueDate().isBlank()
                ? null : LocalDate.parse(req.dueDate());
            boolean timeProvided = Boolean.TRUE.equals(req.clearDueTime())
                || (req.dueTime() != null && !req.dueTime().isBlank());
            LocalTime dueTime = req.dueTime() == null || req.dueTime().isBlank()
                ? null : LocalTime.parse(req.dueTime());
            return repository.update(companyId, id, req.kind(), req.title(), dueDate, dueTime,
                    timeProvided, req.location(), req.status(), req.notes())
                .<ResponseEntity<Object>>map(d -> {
                    auditLogger.log(companyId, user.userId(), "legal_deadline_updated",
                        "legal_deadline", id, Map.of());
                    return ResponseEntity.ok(d);
                })
                .orElseGet(() -> error(404, "Not Found", "deadline_not_found"));
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
    }

    @DeleteMapping("/api/legal/deadlines/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLegal(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (!repository.delete(companyId, id)) {
            return error(404, "Not Found", "deadline_not_found");
        }
        auditLogger.log(companyId, user.userId(), "legal_deadline_deleted", "legal_deadline", id, Map.of());
        return ResponseEntity.noContent().build();
    }
}
