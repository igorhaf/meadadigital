package com.meada.whatsapp.profiles.pousada.reservations;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.pousada.PousadaProfileGuard;
import com.meada.whatsapp.profiles.pousada.PousadaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.ConflictException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.InactiveRoomException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.InvalidDatesException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.InvalidStatusException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.OverCapacityException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.ReservationNotFoundException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.RoomNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reservas do tenant pousada (camada 7.6). TENANT + perfil 'pousada' only. READ (filtros) + POST
 * manual (sem WhatsApp) + transição de status. NÃO há DELETE — histórico; "remover" = cancelado.
 */
@RestController
public class PousadaReservationController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PousadaReservationService service;
    private final PousadaProfileGuard profileGuard;

    public PousadaReservationController(PousadaReservationService service, PousadaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação manual (tenant). checkIn/checkOut em "YYYY-MM-DD". */
    public record CreateReservationRequest(
        @NotNull UUID roomId,
        @NotBlank @Size(max = 200) String guestName,
        @Size(max = 40) String guestPhone,
        @Min(1) int guestsCount,
        @NotBlank String checkIn,
        @NotBlank String checkOut,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/pousada/reservations")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) UUID roomId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = dateFrom == null || dateFrom.isBlank() ? null : LocalDate.parse(dateFrom);
            to = dateTo == null || dateTo.isBlank() ? null : LocalDate.parse(dateTo);
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, from, to, roomId, contactId);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, from, to, roomId, contactId, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/pousada/reservations/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "reservation_not_found"));
    }

    @PostMapping("/api/pousada/reservations")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateReservationRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate checkIn;
        LocalDate checkOut;
        try {
            checkIn = LocalDate.parse(req.checkIn());
            checkOut = LocalDate.parse(req.checkOut());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_dates");
        }
        try {
            PousadaReservation created = service.create(companyId, req.roomId(), null, null,
                checkIn, checkOut, req.guestsCount(), req.guestName(), req.guestPhone(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (RoomNotFoundException e) {
            return error(404, "Not Found", "room_not_found");
        } catch (InactiveRoomException e) {
            return error(400, "Bad Request", "inactive_room");
        } catch (InvalidDatesException e) {
            return error(400, "Bad Request", "invalid_dates");
        } catch (OverCapacityException e) {
            return error(400, "Bad Request", "over_capacity");
        } catch (ConflictException e) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Conflict");
            body.put("reason", "conflict_dates");
            PousadaReservationConflict c = e.conflict();
            if (c != null) {
                body.put("conflict", Map.of(
                    "reservationId", c.existingId().toString(),
                    "guestName", c.existingGuestName(),
                    "checkInDate", c.existingCheckInDate().toString(),
                    "checkOutDate", c.existingCheckOutDate().toString(),
                    "roomName", c.existingRoomName()));
            }
            return ResponseEntity.status(409).body(body);
        }
    }

    @PatchMapping("/api/pousada/reservations/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePousada(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (ReservationNotFoundException e) {
            return error(404, "Not Found", "reservation_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
