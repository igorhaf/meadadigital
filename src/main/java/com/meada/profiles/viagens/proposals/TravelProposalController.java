package com.meada.profiles.viagens.proposals;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.viagens.ViagensProfileGuard;
import com.meada.profiles.viagens.ViagensProfileGuard.WrongProfileException;
import com.meada.profiles.viagens.proposals.TravelProposalService.ConsultantNotFoundException;
import com.meada.profiles.viagens.proposals.TravelProposalService.EmptyBudgetException;
import com.meada.profiles.viagens.proposals.TravelProposalService.InactiveConsultantException;
import com.meada.profiles.viagens.proposals.TravelProposalService.InvalidStatusException;
import com.meada.profiles.viagens.proposals.TravelProposalService.InvalidStatusTransitionException;
import com.meada.profiles.viagens.proposals.TravelProposalService.ItemNotFoundException;
import com.meada.profiles.viagens.proposals.TravelProposalService.ItineraryDayNotFoundException;
import com.meada.profiles.viagens.proposals.TravelProposalService.ProposalLockedException;
import com.meada.profiles.viagens.proposals.TravelProposalService.ProposalNotFoundException;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Propostas de viagem do tenant viagens (camada 8.18 / perfil viagens). TENANT + perfil 'viagens'
 * only. Espelho EXATO do EventProposalController (chassi eventos 8.2): cotação (com category) +
 * ITINERÁRIO MULTI-DIA (add/update/delete/reorder, a escapada) + status. start_date/end_date/day_date
 * são campos LIVRES (sem conflito — é cotação).
 */
@RestController
public class TravelProposalController {

    private static final int MAX_PAGE_SIZE = 200;

    private final TravelProposalService service;
    private final ViagensProfileGuard profileGuard;

    public TravelProposalController(TravelProposalService service, ViagensProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record OpenRequest(
        UUID contactId,
        String customerName,
        UUID consultantId,
        String destination,
        String startDate,       // ISO date yyyy-MM-dd
        String endDate,         // ISO date yyyy-MM-dd
        Integer numTravelers,
        String travelStyle,
        String briefing,
        String notes) {}

    public record UpdateFieldsRequest(
        UUID consultantId,
        Boolean clearConsultant,
        String destination,
        Boolean clearDestination,
        String startDate,
        Boolean clearStartDate,
        String endDate,
        Boolean clearEndDate,
        Integer numTravelers,
        String travelStyle,
        Boolean clearTravelStyle,
        String briefing,
        String notes) {}

    public record ItemRequest(
        String category,
        @NotBlank @Size(max = 200) String description,
        @Min(1) int quantity,
        @Min(0) int unitPriceCents) {}

    public record ItemUpdateRequest(String category, String description, Integer quantity, Integer unitPriceCents) {}

    public record ItineraryRequest(
        String dayDate,         // ISO date yyyy-MM-dd (opcional)
        @NotBlank @Size(max = 200) String title,
        String description) {}

    public record ItineraryUpdateRequest(Integer dayNumber, String dayDate, Boolean clearDayDate,
                                         String title, String description, Boolean clearDescription) {}

    public record ReorderRequest(List<UUID> orderedIds) {}

    public record StatusRequest(String newStatus) {}

    public record DepositRequest(Integer depositCents, Boolean depositPaid) {}

    @GetMapping("/api/viagens/proposals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID consultantId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String startDateFrom,
            @RequestParam(required = false) String startDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate from;
        LocalDate to;
        try {
            from = startDateFrom == null || startDateFrom.isBlank() ? null : LocalDate.parse(startDateFrom);
            to = startDateTo == null || startDateTo.isBlank() ? null : LocalDate.parse(startDateTo);
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, consultantId, contactId, from, to);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, consultantId, contactId, from, to, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/viagens/proposals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "proposal_not_found"));
    }

    @PostMapping("/api/viagens/proposals")
    public ResponseEntity<Object> open(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody OpenRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = req.startDate() == null || req.startDate().isBlank() ? null : LocalDate.parse(req.startDate());
            endDate = req.endDate() == null || req.endDate().isBlank() ? null : LocalDate.parse(req.endDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        if (req.numTravelers() != null && req.numTravelers() < 1) {
            return error(400, "Bad Request", "invalid_num_travelers");
        }
        try {
            TravelProposal created = service.open(companyId, req.contactId(), req.customerName(), req.consultantId(),
                null, req.destination(), startDate, endDate, req.numTravelers(), req.travelStyle(), req.briefing(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ConsultantNotFoundException e) {
            return error(404, "Not Found", "consultant_not_found");
        } catch (InactiveConsultantException e) {
            return error(400, "Bad Request", "inactive_consultant");
        }
    }

    @PatchMapping("/api/viagens/proposals/{id}")
    public ResponseEntity<Object> updateFields(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateFieldsRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean consultantProvided = req.consultantId() != null || Boolean.TRUE.equals(req.clearConsultant());
        UUID consultantId = Boolean.TRUE.equals(req.clearConsultant()) ? null : req.consultantId();
        boolean destinationProvided = req.destination() != null || Boolean.TRUE.equals(req.clearDestination());
        String destination = Boolean.TRUE.equals(req.clearDestination()) ? null : req.destination();
        boolean startProvided = req.startDate() != null || Boolean.TRUE.equals(req.clearStartDate());
        boolean endProvided = req.endDate() != null || Boolean.TRUE.equals(req.clearEndDate());
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = Boolean.TRUE.equals(req.clearStartDate()) || req.startDate() == null || req.startDate().isBlank()
                ? null : LocalDate.parse(req.startDate());
            endDate = Boolean.TRUE.equals(req.clearEndDate()) || req.endDate() == null || req.endDate().isBlank()
                ? null : LocalDate.parse(req.endDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        boolean travelersProvided = req.numTravelers() != null;
        if (req.numTravelers() != null && req.numTravelers() < 1) {
            return error(400, "Bad Request", "invalid_num_travelers");
        }
        boolean styleProvided = req.travelStyle() != null || Boolean.TRUE.equals(req.clearTravelStyle());
        String travelStyle = Boolean.TRUE.equals(req.clearTravelStyle()) ? null : req.travelStyle();
        try {
            return ResponseEntity.ok(service.updateFields(companyId, id, consultantId, consultantProvided,
                destination, destinationProvided, startDate, startProvided, endDate, endProvided,
                req.numTravelers(), travelersProvided, travelStyle, styleProvided, req.briefing(), req.notes()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ConsultantNotFoundException e) {
            return error(404, "Not Found", "consultant_not_found");
        } catch (InactiveConsultantException e) {
            return error(400, "Bad Request", "inactive_consultant");
        }
    }

    // ---- Itens de COTAÇÃO ----

    @PostMapping("/api/viagens/proposals/{id}/items")
    public ResponseEntity<Object> addItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            TravelProposalItem item = service.addItem(companyId, id, req.category(), req.description(),
                req.quantity(), req.unitPriceCents());
            return ResponseEntity.status(201).body(item);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/viagens/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> updateItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ItemUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateItem(companyId, id, itemId, req.category(), req.description(),
                req.quantity(), req.unitPriceCents()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/viagens/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> deleteItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
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

    // ---- Dias de ITINERÁRIO (a escapada multi-dia) ----

    @PostMapping("/api/viagens/proposals/{id}/itinerary")
    public ResponseEntity<Object> addItineraryDay(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ItineraryRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate dayDate;
        try {
            dayDate = req.dayDate() == null || req.dayDate().isBlank() ? null : LocalDate.parse(req.dayDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            TravelItineraryDay day = service.addItineraryDay(companyId, id, dayDate, req.title(), req.description());
            return ResponseEntity.status(201).body(day);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/viagens/proposals/{id}/itinerary/{dayId}")
    public ResponseEntity<Object> updateItineraryDay(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID dayId, @RequestBody ItineraryUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean dayNumberProvided = req.dayNumber() != null;
        boolean dateProvided = req.dayDate() != null || Boolean.TRUE.equals(req.clearDayDate());
        LocalDate dayDate;
        try {
            dayDate = Boolean.TRUE.equals(req.clearDayDate()) || req.dayDate() == null || req.dayDate().isBlank()
                ? null : LocalDate.parse(req.dayDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        boolean descProvided = req.description() != null || Boolean.TRUE.equals(req.clearDescription());
        String description = Boolean.TRUE.equals(req.clearDescription()) ? null : req.description();
        try {
            return ResponseEntity.ok(service.updateItineraryDay(companyId, id, dayId, req.dayNumber(),
                dayNumberProvided, dayDate, dateProvided, req.title(), description, descProvided));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ItineraryDayNotFoundException e) {
            return error(404, "Not Found", "itinerary_day_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/viagens/proposals/{id}/itinerary/{dayId}")
    public ResponseEntity<Object> deleteItineraryDay(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID dayId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteItineraryDay(companyId, id, dayId);
            return ResponseEntity.noContent().build();
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ItineraryDayNotFoundException e) {
            return error(404, "Not Found", "itinerary_day_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    /** Re-ordena o itinerário: recebe a nova ordem (lista de ids) → re-materializa day_number 1..N. */
    @PatchMapping("/api/viagens/proposals/{id}/itinerary/reorder")
    public ResponseEntity<Object> reorderItinerary(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ReorderRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        List<UUID> orderedIds = req.orderedIds() == null ? List.of() : req.orderedIds();
        try {
            return ResponseEntity.ok(Map.of("itinerary", service.reorderItinerary(companyId, id, orderedIds)));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ItineraryDayNotFoundException e) {
            return error(404, "Not Found", "itinerary_day_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    // ---- Status ----

    @PatchMapping("/api/viagens/proposals/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
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
        } catch (TravelProposalService.DepositRequiredException e) {
            return error(409, "Conflict", "deposit_required");
        }
    }

    /** Registra o sinal/entrada e/ou marca como recebido (onda #1 — manual até o gateway #50). */
    @PatchMapping("/api/viagens/proposals/{id}/deposit")
    public ResponseEntity<Object> setDeposit(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody DepositRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireViagens(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.setDeposit(companyId, id, req.depositCents(),
                Boolean.TRUE.equals(req.depositPaid())));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        } catch (TravelProposalService.InvalidDepositException e) {
            return error(400, "Bad Request", "invalid_deposit");
        }
    }
}
