package com.meada.whatsapp.profiles.escola.visits;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.InvalidPeriodException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.InvalidStatusException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.PastDateException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.StudentNotFoundException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.VisitNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Visitas agendadas do tenant escola (camada 8.19, ESCAPADA 2). TENANT + perfil 'escola' only.
 * READ + POST manual + PATCH status. NÃO há DELETE — "remover" = cancelar.
 */
@RestController
public class EscolaVisitController {

    private static final int MAX_PAGE_SIZE = 200;

    private final EscolaVisitService service;
    private final EscolaProfileGuard profileGuard;

    public EscolaVisitController(EscolaVisitService service, EscolaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateVisitRequest(
        @NotBlank @Size(max = 200) String visitorName,
        @Size(max = 40) String visitorPhone,
        @NotBlank String visitDate,
        @NotBlank String period,
        Integer numPeople,
        UUID studentId,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/escola/visits")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
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
        long total = service.count(companyId, status);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/escola/visits/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "visit_not_found"));
    }

    @PostMapping("/api/escola/visits")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateVisitRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate visitDate;
        try {
            visitDate = LocalDate.parse(req.visitDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            EscolaVisit created = service.create(companyId, null, null, req.studentId(), req.visitorName(),
                req.visitorPhone(), visitDate, req.period(), req.numPeople(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (InvalidPeriodException e) {
            return error(400, "Bad Request", "invalid_period");
        } catch (PastDateException e) {
            return error(422, "Unprocessable Entity", "past_date");
        } catch (StudentNotFoundException e) {
            return error(404, "Not Found", "student_not_found");
        }
    }

    @PatchMapping("/api/escola/visits/{id}/status")
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
        } catch (VisitNotFoundException e) {
            return error(404, "Not Found", "visit_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
