package com.meada.profiles.eventos.proposals;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.eventos.EventosProfileGuard;
import com.meada.profiles.eventos.EventosProfileGuard.WrongProfileException;
import com.meada.profiles.eventos.proposals.EventProposalService.EmptyBudgetException;
import com.meada.profiles.eventos.proposals.EventProposalService.InactivePlannerException;
import com.meada.profiles.eventos.proposals.EventProposalService.InvalidStatusException;
import com.meada.profiles.eventos.proposals.EventProposalService.InvalidStatusTransitionException;
import com.meada.profiles.eventos.proposals.EventProposalService.ItemNotFoundException;
import com.meada.profiles.eventos.proposals.EventProposalService.PlannerNotFoundException;
import com.meada.profiles.eventos.proposals.EventProposalService.ProposalLockedException;
import com.meada.profiles.eventos.proposals.EventProposalService.ProposalNotFoundException;
import com.meada.profiles.eventos.proposals.EventProposalService.TimelineItemNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Propostas de evento do tenant eventos (camada 8.2). TENANT + perfil 'eventos' only. */
@RestController
public class EventProposalController {

    private static final int MAX_PAGE_SIZE = 200;

    private final EventProposalService service;
    private final EventosProfileGuard profileGuard;

    public EventProposalController(EventProposalService service, EventosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record OpenRequest(
        UUID contactId,
        String customerName,
        UUID plannerId,
        String eventType,
        String eventDate,        // ISO date yyyy-MM-dd
        Integer guestCount,
        String briefing,
        String notes) {}

    public record UpdateFieldsRequest(
        UUID plannerId,
        Boolean clearPlanner,
        String eventType,
        String eventDate,
        Boolean clearEventDate,
        Integer guestCount,
        Boolean clearGuestCount,
        String briefing,
        String notes) {}

    public record ItemRequest(
        @NotBlank @Size(max = 200) String description,
        @Min(1) int quantity,
        @Min(0) int unitPriceCents) {}

    public record ItemUpdateRequest(String description, Integer quantity, Integer unitPriceCents) {}

    public record TimelineRequest(
        @NotBlank String startTime,    // HH:MM
        @NotBlank @Size(max = 200) String title,
        String description) {}

    public record TimelineUpdateRequest(String startTime, String title, String description, Boolean clearDescription) {}

    public record StatusRequest(String newStatus) {}

    /**
     * Onda 1 (backlog #3): checagem LEVE de data ocupada — existe proposta aprovada/fechada/
     * realizada na mesma data? Aviso NÃO bloqueante (a casa pode ter 2 salões; quem decide é a
     * equipe). {@code excludeId} exclui a própria proposta na edição.
     */
    @GetMapping("/api/eventos/proposals/date-check")
    public ResponseEntity<Object> dateCheck(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam String date,
            @RequestParam(required = false) UUID excludeId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        java.time.LocalDate d;
        try {
            d = java.time.LocalDate.parse(date);
        } catch (java.time.DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        long count = service.countOccupied(companyId, d, excludeId);
        return ResponseEntity.ok(Map.of("occupied", count > 0, "count", count));
    }

    @GetMapping("/api/eventos/proposals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID plannerId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String eventDateFrom,
            @RequestParam(required = false) String eventDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = eventDateFrom == null || eventDateFrom.isBlank() ? null : LocalDate.parse(eventDateFrom);
            to = eventDateTo == null || eventDateTo.isBlank() ? null : LocalDate.parse(eventDateTo);
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, plannerId, contactId, from, to);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, plannerId, contactId, from, to, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/eventos/proposals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "proposal_not_found"));
    }

    @PostMapping("/api/eventos/proposals")
    public ResponseEntity<Object> open(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody OpenRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate eventDate;
        try {
            eventDate = req.eventDate() == null || req.eventDate().isBlank() ? null : LocalDate.parse(req.eventDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        if (req.guestCount() != null && req.guestCount() < 0) {
            return error(400, "Bad Request", "invalid_guest_count");
        }
        try {
            EventProposal created = service.open(companyId, req.contactId(), req.customerName(), req.plannerId(),
                null, req.eventType(), eventDate, req.guestCount(), req.briefing(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (PlannerNotFoundException e) {
            return error(404, "Not Found", "planner_not_found");
        } catch (InactivePlannerException e) {
            return error(400, "Bad Request", "inactive_planner");
        }
    }

    @PatchMapping("/api/eventos/proposals/{id}")
    public ResponseEntity<Object> updateFields(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateFieldsRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean plannerProvided = req.plannerId() != null || Boolean.TRUE.equals(req.clearPlanner());
        UUID plannerId = Boolean.TRUE.equals(req.clearPlanner()) ? null : req.plannerId();
        boolean dateProvided = req.eventDate() != null || Boolean.TRUE.equals(req.clearEventDate());
        LocalDate eventDate;
        try {
            eventDate = Boolean.TRUE.equals(req.clearEventDate()) || req.eventDate() == null || req.eventDate().isBlank()
                ? null : LocalDate.parse(req.eventDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        boolean guestProvided = req.guestCount() != null || Boolean.TRUE.equals(req.clearGuestCount());
        Integer guestCount = Boolean.TRUE.equals(req.clearGuestCount()) ? null : req.guestCount();
        if (guestCount != null && guestCount < 0) {
            return error(400, "Bad Request", "invalid_guest_count");
        }
        try {
            return ResponseEntity.ok(service.updateFields(companyId, id, plannerId, plannerProvided,
                req.eventType(), eventDate, dateProvided, guestCount, guestProvided, req.briefing(), req.notes()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (PlannerNotFoundException e) {
            return error(404, "Not Found", "planner_not_found");
        } catch (InactivePlannerException e) {
            return error(400, "Bad Request", "inactive_planner");
        }
    }

    // ---- Itens de ORÇAMENTO ----

    @PostMapping("/api/eventos/proposals/{id}/items")
    public ResponseEntity<Object> addItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            EventProposalItem item = service.addItem(companyId, id, req.description(), req.quantity(), req.unitPriceCents());
            return ResponseEntity.status(201).body(item);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/eventos/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> updateItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ItemUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateItem(companyId, id, itemId, req.description(),
                req.quantity(), req.unitPriceCents()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/eventos/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> deleteItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteItem(companyId, id, itemId);
            return ResponseEntity.noContent().build();
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    // ---- Marcos de CRONOGRAMA (a escapada) ----

    @PostMapping("/api/eventos/proposals/{id}/timeline")
    public ResponseEntity<Object> addTimelineItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody TimelineRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalTime startTime;
        try {
            startTime = LocalTime.parse(req.startTime());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_time");
        }
        try {
            EventTimelineItem item = service.addTimelineItem(companyId, id, startTime, req.title(), req.description());
            return ResponseEntity.status(201).body(item);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/eventos/proposals/{id}/timeline/{itemId}")
    public ResponseEntity<Object> updateTimelineItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody TimelineUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean timeProvided = req.startTime() != null && !req.startTime().isBlank();
        LocalTime startTime;
        try {
            startTime = timeProvided ? LocalTime.parse(req.startTime()) : null;
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_time");
        }
        boolean descProvided = req.description() != null || Boolean.TRUE.equals(req.clearDescription());
        String description = Boolean.TRUE.equals(req.clearDescription()) ? null : req.description();
        try {
            return ResponseEntity.ok(service.updateTimelineItem(companyId, id, itemId, startTime, timeProvided,
                req.title(), description, descProvided));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (TimelineItemNotFoundException e) {
            return error(404, "Not Found", "timeline_item_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/eventos/proposals/{id}/timeline/{itemId}")
    public ResponseEntity<Object> deleteTimelineItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteTimelineItem(companyId, id, itemId);
            return ResponseEntity.noContent().build();
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (TimelineItemNotFoundException e) {
            return error(404, "Not Found", "timeline_item_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    // ---- Status ----

    @PatchMapping("/api/eventos/proposals/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEventos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateStatus(companyId, id, req.newStatus()));
        } catch (InvalidStatusException e) {
            return error(400, "Bad Request", "invalid_status");
        } catch (EmptyBudgetException e) {
            return error(400, "Bad Request", "empty_budget");
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (InvalidStatusTransitionException e) {
            return error(409, "Conflict", "invalid_status_transition");
        }
    }
}
