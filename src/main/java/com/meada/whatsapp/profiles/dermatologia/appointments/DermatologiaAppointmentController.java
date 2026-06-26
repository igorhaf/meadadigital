package com.meada.whatsapp.profiles.dermatologia.appointments;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaProfileGuard;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.AppointmentNotFoundException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.ConflictException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.InactivePatientException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.InactiveProcedureTypeException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.InactiveProfessionalException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.InvalidStatusException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.OutsideHoursException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.PatientNotFoundException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.ProcedureTypeNotFoundException;
import com.meada.whatsapp.profiles.dermatologia.appointments.DermatologiaAppointmentService.ProfessionalNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

import java.time.DateTimeException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Consultas do tenant dermatologia (camada 8.11). TENANT + perfil 'dermatologia' only. */
@RestController
public class DermatologiaAppointmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final DermatologiaAppointmentService service;
    private final DermatologiaProfileGuard profileGuard;

    public DermatologiaAppointmentController(DermatologiaAppointmentService service, DermatologiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual. startAt em ISO-8601 instant. */
    public record CreateRequest(
        @NotNull UUID professionalId,
        @NotNull UUID patientId,
        @NotNull UUID procedureTypeId,
        @NotBlank String startAt,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/dermatologia/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID professionalId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
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
        long total = service.count(companyId, status, from, to, professionalId, patientId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, professionalId, patientId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/dermatologia/appointments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "appointment_not_found"));
    }

    @PostMapping("/api/dermatologia/appointments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
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
            DermatologiaAppointment created = service.create(companyId, req.professionalId(), req.patientId(),
                req.procedureTypeId(), null, startAt, req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ProfessionalNotFoundException e) {
            return error(404, "Not Found", "professional_not_found");
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        } catch (ProcedureTypeNotFoundException e) {
            return error(404, "Not Found", "procedure_type_not_found");
        } catch (InactiveProfessionalException e) {
            return error(400, "Bad Request", "inactive_professional");
        } catch (InactivePatientException e) {
            return error(400, "Bad Request", "inactive_patient");
        } catch (InactiveProcedureTypeException e) {
            return error(400, "Bad Request", "inactive_procedure_type");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            DermatologiaAppointmentConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "appointmentId", c.existingId().toString(),
                    "patientName", c.existingPatientName(),
                    "startAt", c.existingStartAt().toString(),
                    "endAt", c.existingEndAt().toString()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    @PatchMapping("/api/dermatologia/appointments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
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
