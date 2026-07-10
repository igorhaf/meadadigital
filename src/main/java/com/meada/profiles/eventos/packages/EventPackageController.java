package com.meada.profiles.eventos.packages;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.common.audit.AuditLogger;
import com.meada.profiles.eventos.EventosContextCache;
import com.meada.profiles.eventos.EventosProfileGuard;
import com.meada.profiles.eventos.EventosProfileGuard.WrongProfileException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
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
import java.util.Set;
import java.util.UUID;

/**
 * Catálogo de pacotes/adicionais do buffet (onda Eventos 1, backlog #2). TENANT + perfil
 * 'eventos'. Mutação invalida o {@link EventosContextCache} (o catálogo entra no prompt).
 */
@RestController
public class EventPackageController {

    private static final Set<String> KINDS = Set.of("pacote", "adicional");

    private final EventPackageRepository repository;
    private final EventosProfileGuard profileGuard;
    private final AuditLogger auditLogger;
    private final EventosContextCache contextCache;

    public EventPackageController(EventPackageRepository repository, EventosProfileGuard profileGuard,
                                  AuditLogger auditLogger, EventosContextCache contextCache) {
        this.repository = repository;
        this.profileGuard = profileGuard;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(@NotBlank String name, String kind, String description,
                                @PositiveOrZero int priceCents, Boolean suggestible, Boolean active) {}

    public record UpdateRequest(String name, String kind, String description, Integer priceCents,
                                Boolean suggestible, Boolean active) {}

    @GetMapping("/api/eventos/packages")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", repository.listByCompany(companyId, false)));
    }

    @PostMapping("/api/eventos/packages")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String kind = req.kind() == null || req.kind().isBlank() ? "pacote" : req.kind();
        if (!KINDS.contains(kind)) {
            return error(400, "Bad Request", "invalid_kind");
        }
        EventPackage created = repository.insert(companyId, req.name(), kind,
            req.description() == null || req.description().isBlank() ? null : req.description().strip(),
            req.priceCents(), Boolean.TRUE.equals(req.suggestible()),
            req.active() == null || req.active());
        auditLogger.log(companyId, user.userId(), "event_package_created", "event_package",
            created.id(), Map.of("kind", kind));
        contextCache.invalidate(companyId);
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/eventos/packages/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (req.kind() != null && !req.kind().isBlank() && !KINDS.contains(req.kind())) {
            return error(400, "Bad Request", "invalid_kind");
        }
        if (req.priceCents() != null && req.priceCents() < 0) {
            return error(400, "Bad Request", "invalid_price");
        }
        return repository.update(companyId, id, req.name(), req.kind(), req.description(),
                req.priceCents(), req.suggestible(), req.active())
            .<ResponseEntity<Object>>map(p -> {
                auditLogger.log(companyId, user.userId(), "event_package_updated", "event_package",
                    id, Map.of());
                contextCache.invalidate(companyId);
                return ResponseEntity.ok(p);
            })
            .orElseGet(() -> error(404, "Not Found", "package_not_found"));
    }

    @DeleteMapping("/api/eventos/packages/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        if (!repository.delete(companyId, id)) {
            return error(404, "Not Found", "package_not_found");
        }
        auditLogger.log(companyId, user.userId(), "event_package_deleted", "event_package", id, Map.of());
        contextCache.invalidate(companyId);
        return ResponseEntity.noContent().build();
    }
}
