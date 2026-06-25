package com.meada.whatsapp.profiles.atelie.proposals;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.atelie.AtelieProfileGuard;
import com.meada.whatsapp.profiles.atelie.AtelieProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.ArtisanNotFoundException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.EmptyBudgetException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.FittingNotFoundException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.InactiveArtisanException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.InvalidFittingStatusException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.InvalidStatusException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.ItemNotFoundException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.ProposalLockedException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.ProposalNotFoundException;
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

/** Propostas de ateliê do tenant atelie (camada 8.14). TENANT + perfil 'atelie' only. */
@RestController
public class AtelieProposalController {

    private static final int MAX_PAGE_SIZE = 200;

    private final AtelieProposalService service;
    private final AtelieProfileGuard profileGuard;

    public AtelieProposalController(AtelieProposalService service, AtelieProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record OpenRequest(
        UUID contactId,
        String customerName,
        UUID artisanId,
        String projectType,
        String occasion,
        String estimatedDate,    // ISO date yyyy-MM-dd
        String briefing,
        String notes) {}

    public record UpdateFieldsRequest(
        UUID artisanId,
        Boolean clearArtisan,
        String projectType,
        String occasion,
        String estimatedDate,
        Boolean clearEstimatedDate,
        String briefing,
        String notes) {}

    public record ItemRequest(
        @NotBlank @Size(max = 200) String description,
        @Min(1) int quantity,
        @Min(0) int unitPriceCents) {}

    public record ItemUpdateRequest(String description, Integer quantity, Integer unitPriceCents) {}

    public record FittingRequest(
        @NotBlank @Size(max = 200) String title,
        String description,
        String dueDate) {}     // ISO date yyyy-MM-dd | null

    public record FittingUpdateRequest(String title, String description, Boolean clearDescription,
                                       String dueDate, Boolean clearDueDate) {}

    public record FittingReorderRequest(List<UUID> orderedIds) {}

    public record FittingStatusRequest(String status) {}

    public record StatusRequest(String newStatus) {}

    @GetMapping("/api/atelie/proposals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID artisanId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String projectType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        long total = service.count(companyId, status, artisanId, contactId, projectType);
        return ResponseEntity.ok(Map.of(
            "items", service.list(companyId, status, artisanId, contactId, projectType, size, safePage * size),
            "total", total, "page", safePage, "pageSize", size));
    }

    @GetMapping("/api/atelie/proposals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "proposal_not_found"));
    }

    @PostMapping("/api/atelie/proposals")
    public ResponseEntity<Object> open(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestBody OpenRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate estimatedDate;
        try {
            estimatedDate = req.estimatedDate() == null || req.estimatedDate().isBlank() ? null : LocalDate.parse(req.estimatedDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            AtelieProposal created = service.open(companyId, req.contactId(), req.customerName(), req.artisanId(),
                null, req.projectType(), req.occasion(), estimatedDate, req.briefing(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ArtisanNotFoundException e) {
            return error(404, "Not Found", "artisan_not_found");
        } catch (InactiveArtisanException e) {
            return error(400, "Bad Request", "inactive_artisan");
        }
    }

    @PatchMapping("/api/atelie/proposals/{id}")
    public ResponseEntity<Object> updateFields(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody UpdateFieldsRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean artisanProvided = req.artisanId() != null || Boolean.TRUE.equals(req.clearArtisan());
        UUID artisanId = Boolean.TRUE.equals(req.clearArtisan()) ? null : req.artisanId();
        boolean dateProvided = req.estimatedDate() != null || Boolean.TRUE.equals(req.clearEstimatedDate());
        LocalDate estimatedDate;
        try {
            estimatedDate = Boolean.TRUE.equals(req.clearEstimatedDate()) || req.estimatedDate() == null || req.estimatedDate().isBlank()
                ? null : LocalDate.parse(req.estimatedDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.updateFields(companyId, id, artisanId, artisanProvided,
                req.projectType(), req.occasion(), estimatedDate, dateProvided, req.briefing(), req.notes()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ArtisanNotFoundException e) {
            return error(404, "Not Found", "artisan_not_found");
        } catch (InactiveArtisanException e) {
            return error(400, "Bad Request", "inactive_artisan");
        }
    }

    // ---- Itens de ORÇAMENTO ----

    @PostMapping("/api/atelie/proposals/{id}/items")
    public ResponseEntity<Object> addItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            AtelieProposalItem item = service.addItem(companyId, id, req.description(), req.quantity(), req.unitPriceCents());
            return ResponseEntity.status(201).body(item);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/atelie/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> updateItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId, @RequestBody ItemUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
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

    @DeleteMapping("/api/atelie/proposals/{id}/items/{itemId}")
    public ResponseEntity<Object> deleteItem(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
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

    // ---- Provas/ajustes (a escapada) ----

    @PostMapping("/api/atelie/proposals/{id}/fittings")
    public ResponseEntity<Object> addFitting(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody FittingRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
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
            AtelieFitting fitting = service.addFitting(companyId, id, req.title(), req.description(), dueDate);
            return ResponseEntity.status(201).body(fitting);
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/atelie/proposals/{id}/fittings/{fittingId}")
    public ResponseEntity<Object> updateFitting(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID fittingId, @RequestBody FittingUpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
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
            return ResponseEntity.ok(service.updateFitting(companyId, id, fittingId, req.title(), description,
                descProvided, dueDate, dueProvided));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (FittingNotFoundException e) {
            return error(404, "Not Found", "fitting_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @DeleteMapping("/api/atelie/proposals/{id}/fittings/{fittingId}")
    public ResponseEntity<Object> deleteFitting(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID fittingId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteFitting(companyId, id, fittingId);
            return ResponseEntity.noContent().build();
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (FittingNotFoundException e) {
            return error(404, "Not Found", "fitting_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/atelie/proposals/{id}/fittings/reorder")
    public ResponseEntity<Object> reorderFittings(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody FittingReorderRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        List<UUID> orderedIds = req.orderedIds() == null ? List.of() : req.orderedIds();
        try {
            return ResponseEntity.ok(Map.of("items", service.reorderFittings(companyId, id, orderedIds)));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    @PatchMapping("/api/atelie/proposals/{id}/fittings/{fittingId}/status")
    public ResponseEntity<Object> transitionFitting(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @PathVariable UUID fittingId, @RequestBody FittingStatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.transitionFitting(companyId, id, fittingId, req.status()));
        } catch (ProposalNotFoundException e) {
            return error(404, "Not Found", "proposal_not_found");
        } catch (FittingNotFoundException e) {
            return error(404, "Not Found", "fitting_not_found");
        } catch (InvalidFittingStatusException e) {
            return error(400, "Bad Request", "invalid_fitting_status");
        } catch (ProposalLockedException e) {
            return error(409, "Conflict", "proposal_locked");
        }
    }

    // ---- Status ----

    @PatchMapping("/api/atelie/proposals/{id}/status")
    public ResponseEntity<Object> updateStatus(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody StatusRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
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
