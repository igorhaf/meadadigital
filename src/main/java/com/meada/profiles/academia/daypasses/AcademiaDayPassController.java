package com.meada.profiles.academia.daypasses;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.profiles.academia.daypasses.AcademiaDayPassService.DayPassNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Passes de day-use / aula avulsa do tenant academia (camada 7.7). TENANT + perfil 'academia' only.
 * Só REGISTRO — a cobrança real (Pix/cartão) espera o gateway #50.
 */
@RestController
public class AcademiaDayPassController {

    private final AcademiaDayPassService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaDayPassController(AcademiaDayPassService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body do registro. contactId/classId opcionais; passDate "YYYY-MM-DD" opcional (default hoje). */
    public record CreateDayPassRequest(
        UUID contactId,
        @NotBlank @Size(max = 200) String guestName,
        String guestPhone,
        UUID classId,
        String passDate,
        @PositiveOrZero int priceCents) {}

    @GetMapping("/api/academia/day-passes")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId)));
    }

    @GetMapping("/api/academia/day-passes/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "day_pass_not_found"));
    }

    @PostMapping("/api/academia/day-passes")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateDayPassRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate passDate = null;
        if (req.passDate() != null && !req.passDate().isBlank()) {
            try {
                passDate = LocalDate.parse(req.passDate());
            } catch (DateTimeException e) {
                return error(400, "Bad Request", "invalid_date");
            }
        }
        AcademiaDayPass created = service.create(companyId, user.userId(), req.contactId(),
            req.guestName(), req.guestPhone(), req.classId(), passDate, req.priceCents());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/academia/day-passes/{id}/pay")
    public ResponseEntity<Object> pay(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.markPaid(companyId, user.userId(), id));
        } catch (DayPassNotFoundException e) {
            return error(404, "Not Found", "day_pass_not_found");
        }
    }
}
