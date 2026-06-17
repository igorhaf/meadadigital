package com.meada.whatsapp.profiles.academia.memberships;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.academia.AcademiaProfileGuard;
import com.meada.whatsapp.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.AlreadyActiveException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.ClassFullException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.ClassInactiveException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.ClassNotFoundException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.InvalidStatusException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.MembershipNotFoundException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.NoClassesException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.PlanInactiveException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.PlanNotFoundException;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Matrículas do tenant academia (camada 7.7). TENANT + perfil 'academia' only. READ + POST manual +
 * PATCH status. NÃO há DELETE — histórico; "remover" = cancelar.
 */
@RestController
public class AcademiaMembershipController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AcademiaMembershipService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaMembershipController(AcademiaMembershipService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateMembershipRequest(
        @NotNull UUID planId,
        @NotNull List<UUID> classIds,
        @NotBlank @Size(max = 200) String studentName,
        @Size(max = 40) String studentPhone,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/academia/memberships")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, planId, classId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, planId, classId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/academia/memberships/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "membership_not_found"));
    }

    @PostMapping("/api/academia/memberships")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateMembershipRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            AcademiaMembership created = service.create(companyId, req.planId(), req.classIds(), null, null,
                req.studentName(), req.studentPhone(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (PlanNotFoundException e) {
            return error(404, "Not Found", "plan_not_found");
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        } catch (PlanInactiveException e) {
            return error(400, "Bad Request", "plan_inactive");
        } catch (ClassInactiveException e) {
            return error(400, "Bad Request", "class_inactive");
        } catch (NoClassesException e) {
            return error(400, "Bad Request", "no_classes");
        } catch (AlreadyActiveException e) {
            return error(409, "Conflict", "already_active");
        } catch (ClassFullException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "class_full");
            body.put("classId", e.classId().toString());
            body.put("className", e.className());
            return ResponseEntity.status(409).body(body);
        }
    }

    @PatchMapping("/api/academia/memberships/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (MembershipNotFoundException e) {
            return error(404, "Not Found", "membership_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
