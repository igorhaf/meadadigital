package com.meada.whatsapp.profiles.escola.enrollments;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.AlreadyActiveException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.ClassFullException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.ClassInactiveException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.ClassNotFoundException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.EnrollmentNotFoundException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.InvalidStatusException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.StudentInactiveException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.StudentNotFoundException;
import jakarta.validation.Valid;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Matrículas do tenant escola (camada 8.19). TENANT + perfil 'escola' only. READ + POST manual +
 * PATCH status. NÃO há DELETE — histórico; "remover" = cancelar.
 */
@RestController
public class EscolaEnrollmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final EscolaEnrollmentService service;
    private final EscolaProfileGuard profileGuard;

    public EscolaEnrollmentController(EscolaEnrollmentService service, EscolaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateEnrollmentRequest(
        @NotNull UUID classId,
        @NotNull UUID studentId,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/escola/enrollments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, classId, studentId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, classId, studentId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/escola/enrollments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "enrollment_not_found"));
    }

    @PostMapping("/api/escola/enrollments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateEnrollmentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            EscolaEnrollment created = service.create(companyId, req.classId(), req.studentId(), null, null,
                req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        } catch (StudentNotFoundException e) {
            return error(404, "Not Found", "student_not_found");
        } catch (ClassInactiveException e) {
            return error(400, "Bad Request", "class_inactive");
        } catch (StudentInactiveException e) {
            return error(400, "Bad Request", "student_inactive");
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

    @PatchMapping("/api/escola/enrollments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (EnrollmentNotFoundException e) {
            return error(404, "Not Found", "enrollment_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
