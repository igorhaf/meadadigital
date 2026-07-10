package com.meada.profiles.barbearia.queue;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.barbearia.BarberProfileGuard;
import com.meada.profiles.barbearia.BarberProfileGuard.WrongProfileException;
import com.meada.profiles.barbearia.queue.BarberQueueService.InactiveBarberException;
import com.meada.profiles.barbearia.queue.BarberQueueService.InactiveServiceException;
import com.meada.profiles.barbearia.queue.BarberQueueService.InvalidStatusException;
import com.meada.profiles.barbearia.queue.BarberQueueService.InvalidStatusTransitionException;
import com.meada.profiles.barbearia.queue.BarberQueueService.QueueDisabledException;
import com.meada.profiles.barbearia.queue.BarberQueueService.ServiceNotFoundException;
import com.meada.profiles.barbearia.queue.BarberQueueService.TicketNotFoundException;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * FILA DE WALK-IN do tenant barbearia (camada 8.1) — a escapada desta SM. TENANT + perfil
 * 'barbearia' only. POST entra na fila (manual pelo painel; o handler da IA usa o mesmo service) +
 * GET fila ativa com posição DERIVADA + PATCH transição manual (chamar/atendido/desistiu).
 *
 * <p>NÃO existe callNext automático: "chamar o próximo" = o painel faz PATCH aguardando→chamado no
 * ticket de menor enqueued_at (o front ordena; o back só valida a transição).
 */
@RestController
public class BarberQueueController {

    private final BarberQueueService service;
    private final BarberProfileGuard profileGuard;

    public BarberQueueController(BarberQueueService service, BarberProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Entra na fila. barberId null = "qualquer barbeiro". */
    public record EnqueueRequest(
        UUID barberId,
        @NotNull UUID serviceId,
        @NotBlank @Size(max = 200) String guestName,
        @Size(max = 40) String guestPhone,
        String notes) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/barbearia/queue")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of(
            "items", service.listActive(companyId),
            "waiting", service.queueSize(companyId)));
    }

    @GetMapping("/api/barbearia/queue/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "ticket_not_found"));
    }

    @PostMapping("/api/barbearia/queue")
    public ResponseEntity<Object> enqueue(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody EnqueueRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            BarberQueueTicket created = service.enqueue(companyId, req.barberId(), req.serviceId(),
                null, null, req.guestName(), req.guestPhone(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (QueueDisabledException e) {
            return error(409, "Conflict", "queue_disabled");
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (BarberQueueService.BarberNotFoundException e) {
            return error(404, "Not Found", "barber_not_found");
        } catch (InactiveServiceException e) {
            return error(400, "Bad Request", "inactive_service");
        } catch (InactiveBarberException e) {
            return error(400, "Bad Request", "inactive_barber");
        }
    }

    @PatchMapping("/api/barbearia/queue/{id}/status")
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
        } catch (TicketNotFoundException e) {
            return error(404, "Not Found", "ticket_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }

    public record ConvertRequest(UUID barberId) {}

    /**
     * Onda 2 (backlog #8): "chamar o próximo" vira atendimento — converte o ticket em agendamento
     * IMEDIATO do barbeiro (start=agora) e muta o ticket pra atendido. barberId sobrepõe o do
     * ticket (fila "qualquer barbeiro" → o barbeiro livre que puxou).
     */
    @PostMapping("/api/barbearia/queue/{id}/convert")
    public ResponseEntity<Object> convert(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody(required = false) ConvertRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireBarbearia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(
                service.convertToAppointment(companyId, id, req == null ? null : req.barberId()));
        } catch (TicketNotFoundException e) {
            return error(404, "Not Found", "ticket_not_found");
        } catch (InvalidStatusException | InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        } catch (BarberQueueService.BarberRequiredException e) {
            return error(400, "Bad Request", "barber_required");
        } catch (RuntimeException e) {
            // Inclui conflito de slot do barbeiro (409 conflict_slot da agenda) e afins.
            return error(409, "Conflict", "conflict_slot");
        }
    }
}
