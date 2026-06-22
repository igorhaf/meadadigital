package com.meada.whatsapp.profiles.estetica.appointments;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.AppointmentNotFoundException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.ConflictException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.InactiveProcedureException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.InactiveProfessionalException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.InvalidStatusException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.OutsideHoursException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.PackageExhaustedException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.PackageNotActiveException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.PackageNotFoundException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.PackageWrongContactException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.ProcedureNotFoundException;
import com.meada.whatsapp.profiles.estetica.appointments.AestheticAppointmentService.ProfessionalNotFoundException;
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

import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agendamentos do tenant estetica (camada 8.3). TENANT + perfil 'estetica' only. READ + POST manual
 * (opcionalmente consumindo pacote) + transição de status. NÃO há DELETE — "remover" = cancelado.
 */
@RestController
public class AestheticAppointmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AestheticAppointmentService service;
    private final EsteticaProfileGuard profileGuard;

    public AestheticAppointmentController(AestheticAppointmentService service, EsteticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotNull UUID professionalId,
        @NotNull UUID procedureId,
        UUID packageId,
        @NotBlank @Size(max = 200) String guestName,
        @Size(max = 40) String guestPhone,
        @NotBlank String startAt,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/estetica/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        Instant from;
        Instant to;
        try {
            from = dateFrom == null || dateFrom.isBlank() ? null : Instant.parse(dateFrom);
            to = dateTo == null || dateTo.isBlank() ? null : Instant.parse(dateTo);
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, from, to, professionalId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, professionalId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/estetica/appointments/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "appointment_not_found"));
    }

    @PostMapping("/api/estetica/appointments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        Instant startAt;
        try {
            startAt = Instant.parse(req.startAt());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            // POST manual: sem contactId (o pacote, se houver, é validado por contato — manual sem
            // contato não pode consumir pacote → cai em package_wrong_contact, que é o correto).
            AestheticAppointment created = service.create(companyId, req.professionalId(), req.procedureId(),
                req.packageId(), null, null, startAt, req.guestName(), req.guestPhone(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        } catch (ProcedureNotFoundException e) {
            return error(404, "Not Found", "procedure_not_found");
        } catch (PackageNotFoundException e) {
            return error(404, "Not Found", "package_not_found");
        } catch (InactiveProfessionalException e) {
            return error(400, "Bad Request", "inactive_professional");
        } catch (InactiveProcedureException e) {
            return error(400, "Bad Request", "inactive_procedure");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (PackageNotActiveException e) {
            return error(400, "Bad Request", "package_not_active");
        } catch (PackageWrongContactException e) {
            return error(403, "Forbidden", "package_wrong_contact");
        } catch (PackageExhaustedException e) {
            return error(409, "Conflict", "package_exhausted");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            AestheticAppointmentConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "appointmentId", c.existingId().toString(),
                    "guestName", c.existingGuestName(),
                    "startAt", c.existingStartAt().toString(),
                    "endAt", c.existingEndAt().toString()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    @PatchMapping("/api/estetica/appointments/{id}/status")
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
        } catch (AppointmentNotFoundException e) {
            return error(404, "Not Found", "appointment_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
