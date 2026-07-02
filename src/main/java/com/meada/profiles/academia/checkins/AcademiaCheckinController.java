package com.meada.profiles.academia.checkins;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.profiles.academia.checkins.AcademiaCheckinService.ClassNotFoundException;
import com.meada.profiles.academia.checkins.AcademiaCheckinService.DuplicateCheckinException;
import com.meada.profiles.academia.checkins.AcademiaCheckinService.MembershipNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Check-ins / frequência do tenant academia (camada 7.7, feature #4). TENANT + perfil 'academia' only.
 * POST registra a presença de hoje; GET lista por aula/janela. Rotas sob /api/academia/checkins.
 */
@RestController
public class AcademiaCheckinController {

    private final AcademiaCheckinService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaCheckinController(AcademiaCheckinService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do registro de presença. source opcional (ia|painel; default painel). */
    public record RegisterCheckinRequest(
        @NotNull UUID membershipId,
        @NotNull UUID classId,
        String source,
        String notes) {}

    @PostMapping("/api/academia/checkins")
    public ResponseEntity<Object> register(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody RegisterCheckinRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            AcademiaCheckin created = service.register(companyId, user.userId(),
                req.membershipId(), req.classId(), req.source(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (MembershipNotFoundException e) {
            return error(404, "Not Found", "membership_not_found");
        } catch (ClassNotFoundException e) {
            return error(404, "Not Found", "class_not_found");
        } catch (DuplicateCheckinException e) {
            return error(409, "Conflict", "duplicate_checkin");
        }
    }

    @GetMapping("/api/academia/checkins")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = (from == null || from.isBlank()) ? null : LocalDate.parse(from);
            toDate = (to == null || to.isBlank()) ? null : LocalDate.parse(to);
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, classId, fromDate, toDate)));
    }
}
