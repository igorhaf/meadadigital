package com.meada.whatsapp.profiles.estetica.packages;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageService.InvalidSessionsException;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageService.InvalidStatusException;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageService.PackageNotFoundException;
import com.meada.whatsapp.profiles.estetica.packages.AestheticPackageService.ProcedureNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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

/** Pacotes multi-sessão do tenant estetica (camada 8.3). TENANT + perfil 'estetica' only. */
@RestController
public class AestheticPackageController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AestheticPackageService service;
    private final EsteticaProfileGuard profileGuard;

    public AestheticPackageController(AestheticPackageService service, EsteticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Criação manual pelo tenant. contactId opcional (pacote sem vínculo de WhatsApp). */
    public record CreateRequest(
        UUID contactId,
        String customerName,
        @NotNull UUID procedureId,
        @Min(1) int totalSessions,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/estetica/packages")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) UUID procedureId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, contactId, procedureId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, contactId, procedureId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/estetica/packages/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "package_not_found"));
    }

    @PostMapping("/api/estetica/packages")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String customerName = req.customerName() == null || req.customerName().isBlank()
            ? "Cliente" : req.customerName().trim();
        try {
            AestheticPackage created = service.create(companyId, req.contactId(), customerName, null,
                req.procedureId(), null, req.totalSessions(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ProcedureNotFoundException e) {
            return error(404, "Not Found", "procedure_not_found");
        } catch (InvalidSessionsException e) {
            return error(400, "Bad Request", "invalid_sessions");
        }
    }

    @PatchMapping("/api/estetica/packages/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (PackageNotFoundException e) {
            return error(404, "Not Found", "package_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
