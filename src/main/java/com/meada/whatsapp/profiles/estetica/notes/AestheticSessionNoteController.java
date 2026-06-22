package com.meada.whatsapp.profiles.estetica.notes;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard;
import com.meada.whatsapp.profiles.estetica.EsteticaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.estetica.notes.AestheticSessionNoteService.AppointmentCancelledException;
import com.meada.whatsapp.profiles.estetica.notes.AestheticSessionNoteService.AppointmentNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Ficha/evolução por sessão do tenant estetica (camada 8.3). 1:1 com o agendamento. GET + PUT (upsert).
 * TENANT + perfil 'estetica' only.
 */
@RestController
public class AestheticSessionNoteController {

    private final AestheticSessionNoteService service;
    private final EsteticaProfileGuard profileGuard;

    public AestheticSessionNoteController(AestheticSessionNoteService service, EsteticaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record NoteRequest(String treatedArea, String deviceParams, String observations) {}

    @GetMapping("/api/estetica/appointments/{appointmentId}/note")
    public ResponseEntity<Object> get(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID appointmentId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, appointmentId)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "note_not_found"));
    }

    @PutMapping("/api/estetica/appointments/{appointmentId}/note")
    public ResponseEntity<Object> upsert(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID appointmentId, @RequestBody NoteRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEstetica(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.upsert(companyId, appointmentId, req.treatedArea(),
                req.deviceParams(), req.observations()));
        } catch (AppointmentNotFoundException e) {
            return error(404, "Not Found", "appointment_not_found");
        } catch (AppointmentCancelledException e) {
            return error(409, "Conflict", "appointment_cancelled");
        }
    }
}
