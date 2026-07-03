package com.meada.profiles.barbearia.appointments;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.barbearia.BarberProfileGuard;
import com.meada.profiles.barbearia.BarberProfileGuard.WrongProfileException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.AppointmentNotFoundException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.BarberNotFoundException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.ConflictException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.InactiveBarberException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.InactiveServiceException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.InvalidStatusException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.OutsideHoursException;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService.ServiceNotFoundException;
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
 * Agendamentos do tenant barbearia (camada 8.1). TENANT + perfil 'barbearia' only. READ (filtros) +
 * POST manual (sem WhatsApp) + transição de status. NÃO há DELETE — histórico; "remover" = cancelado.
 * Clone de SalonAppointmentController.
 */
@RestController
public class BarberAppointmentController {

    private static final int MAX_PAGE_SIZE = 200;

    private final BarberAppointmentService service;
    private final BarberProfileGuard profileGuard;

    public BarberAppointmentController(BarberAppointmentService service, BarberProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual (tenant). startAt em ISO-8601 instant. */
    public record CreateAppointmentRequest(
        @NotNull UUID barberId,
        @NotNull UUID serviceId,
        @NotBlank @Size(max = 200) String guestName,
        @Size(max = 40) String guestPhone,
        @NotBlank String startAt,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/barbearia/appointments")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID barberId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
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
        long total = service.count(companyId, status, from, to, barberId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, barberId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/barbearia/appointments/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "appointment_not_found"));
    }

    @PostMapping("/api/barbearia/appointments")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateAppointmentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
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
            BarberAppointment created = service.create(companyId, req.barberId(), req.serviceId(),
                null, null, startAt, req.guestName(), req.guestPhone(), req.notes(), null);
            return ResponseEntity.status(201).body(created);
        } catch (BarberNotFoundException e) {
            return error(404, "Not Found", "barber_not_found");
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (InactiveBarberException e) {
            return error(400, "Bad Request", "inactive_barber");
        } catch (InactiveServiceException e) {
            return error(400, "Bad Request", "inactive_service");
        } catch (OutsideHoursException e) {
            return error(400, "Bad Request", "outside_hours");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_slot");
            BarberAppointmentConflict c = e.conflict();
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

    @PatchMapping("/api/barbearia/appointments/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
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
