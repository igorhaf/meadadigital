package com.meada.profiles.las.yield;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.common.audit.AuditLogger;
import com.meada.profiles.las.LasMenuCache;
import com.meada.profiles.las.LasProfileGuard;
import com.meada.profiles.las.LasProfileGuard.WrongProfileException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * Referência de rendimento (onda Lãs 1, backlog #2). TENANT + perfil 'las'. CRUD sob
 * {@code /api/las/yield}; mutação invalida o {@link LasMenuCache} (a referência entra no prompt).
 */
@RestController
public class LasYieldReferenceController {

    private final LasYieldReferenceRepository repository;
    private final LasProfileGuard profileGuard;
    private final AuditLogger auditLogger;
    private final LasMenuCache menuCache;

    public LasYieldReferenceController(LasYieldReferenceRepository repository, LasProfileGuard profileGuard,
                                       AuditLogger auditLogger, LasMenuCache menuCache) {
        this.repository = repository;
        this.profileGuard = profileGuard;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(@NotBlank String pieceType, String yarnSpec,
                                @Min(1) @Max(200) int skeins, String notes, Boolean active) {}

    public record UpdateRequest(String pieceType, String yarnSpec, Integer skeins, String notes,
                                Boolean active) {}

    @GetMapping("/api/las/yield")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", repository.listByCompany(companyId, false)));
    }

    @PostMapping("/api/las/yield")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LasYieldReference created = repository.insert(companyId, req.pieceType().strip(),
            blankToNull(req.yarnSpec()), req.skeins(), blankToNull(req.notes()),
            req.active() == null || req.active());
        auditLogger.log(companyId, user.userId(), "las_yield_created", "las_yield_reference",
            created.id(), Map.of());
        menuCache.invalidate(companyId);
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/las/yield/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (req.skeins() != null && (req.skeins() < 1 || req.skeins() > 200)) {
            return error(400, "Bad Request", "invalid_skeins");
        }
        return repository.update(companyId, id,
                req.pieceType() == null || req.pieceType().isBlank() ? null : req.pieceType().strip(),
                blankToNull(req.yarnSpec()), req.skeins(), blankToNull(req.notes()), req.active())
            .<ResponseEntity<Object>>map(y -> {
                auditLogger.log(companyId, user.userId(), "las_yield_updated", "las_yield_reference",
                    id, Map.of());
                menuCache.invalidate(companyId);
                return ResponseEntity.ok(y);
            })
            .orElseGet(() -> error(404, "Not Found", "yield_not_found"));
    }

    @DeleteMapping("/api/las/yield/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (!repository.delete(companyId, id)) {
            return error(404, "Not Found", "yield_not_found");
        }
        auditLogger.log(companyId, user.userId(), "las_yield_deleted", "las_yield_reference", id, Map.of());
        menuCache.invalidate(companyId);
        return ResponseEntity.noContent().build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
