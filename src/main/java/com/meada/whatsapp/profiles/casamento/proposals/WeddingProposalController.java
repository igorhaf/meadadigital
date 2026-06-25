package com.meada.whatsapp.profiles.casamento.proposals;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.casamento.CasamentoProfileGuard;
import com.meada.whatsapp.profiles.casamento.CasamentoProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.ChecklistTaskNotFoundException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.EmptyBudgetException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.InactivePlannerException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.InvalidStatusException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.ItemNotFoundException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.PlannerNotFoundException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.ProposalLockedException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.ProposalNotFoundException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.TimelineItemNotFoundException;
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

/** Propostas de casamento do tenant casamento (camada 8.7). TENANT + perfil 'casamento' only. */
@RestController
public class WeddingProposalController {

    private static final int MAX_PAGE_SIZE = 200;

    private final WeddingProposalService service;
    private final CasamentoProfileGuard profileGuard;

    public WeddingProposalController(WeddingProposalService service, CasamentoProfileGuard profileGuard) {
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
        String weddingStyle,
        String weddingDate,        // ISO date yyyy-MM-dd
        Integer guestCount,
        String briefing,
        String notes) {}

    public record UpdateFieldsRequest(
        UUID plannerId,
        Boolean clearPlanner,
        String weddingStyle,
        String weddingDate,
        Boolean clearWeddingDate,
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

    public record ChecklistRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        String dueDate) {}        // ISO date yyyy-MM-dd (nullable)

    public record ChecklistUpdateRequest(
        String title,
        String description,
        Boolean clearDescription,
        String dueDate,
        Boolean clearDueDate) {}

    public record ChecklistToggleRequest(boolean done) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/casamento/proposals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID plannerId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String weddingDateFrom,
            @RequestParam(required = false) String weddingDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = weddingDateFrom == null || weddingDateFrom.isBlank() ? null : LocalDate.parse(weddingDateFrom);
            to = weddingDateTo == null || weddingDateTo.isBlank() ? null : LocalDate.parse(weddingDateTo);
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

    @GetMapping("/api/casamento/proposals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "proposal_not_found"));
    }

    @PostMapping("/api/casamento/proposals")
    public ResponseEntity<Object> open(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody OpenRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate weddingDate;
        try {
            weddingDate = req.weddingDate() == null || req.weddingDate().isBlank() ? null : LocalDate.parse(req.weddingDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        if (req.guestCount() != null && req.guestCount() < 0) {
            return error(400, "Bad Request", "invalid_guest_count");
        }
        try {
            WeddingProposal created = service.open(companyId, req.contactId(), req.customerName(), req.plannerId(),
                null, req.weddingStyle(), weddingDate, req.guestCount(), req.briefing(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (PlannerNotFoundException e) {
            return error(404, "Not Found", "planner_not_found");
        } catch (InactivePlannerException e) {
            return error(400, "Bad Request", "inactive_planner");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}")
    public ResponseEntity<Object> updateFields(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateFieldsRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean plannerProvided = req.plannerId() != null || Boolean.TRUE.equals(req.clearPlanner());
        UUID plannerId = Boolean.TRUE.equals(req.clearPlanner()) ? null : req.plannerId();
        boolean dateProvided = req.weddingDate() != null || Boolean.TRUE.equals(req.clearWeddingDate());
        LocalDate weddingDate;
        try {
            weddingDate = Boolean.TRUE.equals(req.clearWeddingDate()) || req.weddingDate() == null || req.weddingDate().isBlank()
                ? null : LocalDate.parse(req.weddingDate());
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
                req.weddingStyle(), weddingDate, dateProvided, guestCount, guestProvided, req.briefing(), req.notes()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (PlannerNotFoundException e) {
            return error(404, "Not Found", "planner_not_found");
        } catch (InactivePlannerException e) {
            return error(400, "Bad Request", "inactive_planner");
        }
    }

    // ---- Itens de ORÇAMENTO ----

    @PostMapping("/api/casamento/proposals/{id}/items")
    public ResponseEntity<Object> addItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            WeddingProposalItem item = service.addItem(companyId, id, req.description(), req.quantity(), req.unitPriceCents());
            return ResponseEntity.status(201).body(item);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> updateItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ItemUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
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

    @DeleteMapping("/api/casamento/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> deleteItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
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

    // ---- Marcos de CRONOGRAMA ----

    @PostMapping("/api/casamento/proposals/{id}/timeline")
    public ResponseEntity<Object> addTimelineItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody TimelineRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
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
            WeddingTimelineItem item = service.addTimelineItem(companyId, id, startTime, req.title(), req.description());
            return ResponseEntity.status(201).body(item);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}/timeline/{itemId}")
    public ResponseEntity<Object> updateTimelineItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody TimelineUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
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

    @DeleteMapping("/api/casamento/proposals/{id}/timeline/{itemId}")
    public ResponseEntity<Object> deleteTimelineItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
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

    // ---- Checklist PRÉ-CASAMENTO (a escapada) ----

    @PostMapping("/api/casamento/proposals/{id}/checklist")
    public ResponseEntity<Object> addChecklistTask(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ChecklistRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate dueDate;
        try {
            dueDate = req.dueDate() == null || req.dueDate().isBlank() ? null : LocalDate.parse(req.dueDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            WeddingChecklistTask task = service.addChecklistTask(companyId, id, req.title(), req.description(), dueDate);
            return ResponseEntity.status(201).body(task);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}/checklist/{taskId}")
    public ResponseEntity<Object> updateChecklistTask(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID taskId, @RequestBody ChecklistUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean descProvided = req.description() != null || Boolean.TRUE.equals(req.clearDescription());
        String description = Boolean.TRUE.equals(req.clearDescription()) ? null : req.description();
        boolean dueProvided = req.dueDate() != null || Boolean.TRUE.equals(req.clearDueDate());
        LocalDate dueDate;
        try {
            dueDate = Boolean.TRUE.equals(req.clearDueDate()) || req.dueDate() == null || req.dueDate().isBlank()
                ? null : LocalDate.parse(req.dueDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.updateChecklistTask(companyId, id, taskId, req.title(),
                description, descProvided, dueDate, dueProvided));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ChecklistTaskNotFoundException e) {
            return error(404, "Not Found", "checklist_task_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/casamento/proposals/{id}/checklist/{taskId}")
    public ResponseEntity<Object> deleteChecklistTask(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID taskId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteChecklistTask(companyId, id, taskId);
            return ResponseEntity.noContent().build();
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ChecklistTaskNotFoundException e) {
            return error(404, "Not Found", "checklist_task_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/casamento/proposals/{id}/checklist/{taskId}/toggle")
    public ResponseEntity<Object> toggleChecklistTask(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID taskId, @RequestBody ChecklistToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleChecklistTask(companyId, id, taskId, req.done()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ChecklistTaskNotFoundException e) {
            return error(404, "Not Found", "checklist_task_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    // ---- Status ----

    @PatchMapping("/api/casamento/proposals/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireCasamento(user);
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
