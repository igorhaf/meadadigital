package com.meada.profiles.papelaria.catalog;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.papelaria.PapelariaProfileGuard;
import com.meada.profiles.papelaria.PapelariaProfileGuard.WrongProfileException;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService.CatalogItemInUseException;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService.CatalogItemNotFoundException;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService.InvalidCategoryException;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogService.OptionNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Catálogo do tenant papelaria (camada 8.15 / perfil papelaria). Clone de
 * {@code com.meada.profiles.padaria.menu.PadariaMenuController} (camada 8.8) — menu→catalog +
 * specs (no lugar de allergens). TENANT + perfil 'papelaria' only — {@link PapelariaProfileGuard}
 * rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob {@code /api/papelaria/catalog/**}.
 */
@RestController
public class PapelariaCatalogController {

    private final PapelariaCatalogService service;
    private final PapelariaProfileGuard profileGuard;

    public PapelariaCatalogController(PapelariaCatalogService service, PapelariaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação de item. category validada contra o enum no service (400 invalid_category). */
    public record CreateItemRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @PositiveOrZero int priceCents,
        @NotBlank String category,
        boolean madeToOrder,
        @PositiveOrZero Integer leadTimeDays,
        String specs) {}

    /** Body de edição de item (PATCH parcial; todos opcionais). clearLeadTime zera o lead_time_days. */
    public record UpdateItemRequest(
        @Size(max = 200) String name,
        String description,
        @PositiveOrZero Integer priceCents,
        String category,
        Boolean madeToOrder,
        @PositiveOrZero Integer leadTimeDays,
        boolean clearLeadTime,
        String specs,
        Boolean available) {}

    public record ToggleRequest(boolean available) {}

    /** Body de criação de opção (modifier). */
    public record CreateOptionRequest(
        @NotBlank @Size(max = 60) String groupLabel,
        @NotBlank @Size(max = 80) String optionLabel,
        @PositiveOrZero int priceDeltaCents,
        int sortOrder) {}

    /** Body de edição de opção (PATCH parcial; todos opcionais). */
    public record UpdateOptionRequest(
        @Size(max = 60) String groupLabel,
        @Size(max = 80) String optionLabel,
        @PositiveOrZero Integer priceDeltaCents,
        Integer sortOrder,
        Boolean available) {}

    // ===== ITENS =============================================================

    // ---- GET lista ----------------------------------------------------------
    @GetMapping("/api/papelaria/catalog")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, available)));
    }

    // ---- GET detalhe (com options) ------------------------------------------
    @GetMapping("/api/papelaria/catalog/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "catalog_item_not_found"));
    }

    // ---- Faixas de tiragem (onda 1, backlog #2) ------------------------------

    public record TierRequest(java.util.List<PapelariaItemTier> tiers) {}

    @GetMapping("/api/papelaria/catalog/{id}/tiers")
    public ResponseEntity<Object> listTiers(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("items", service.listTiers(companyId, id)));
        } catch (PapelariaCatalogService.CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    @PutMapping("/api/papelaria/catalog/{id}/tiers")
    public ResponseEntity<Object> putTiers(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody TierRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("items", service.replaceTiers(companyId, user.userId(), id,
                req.tiers() == null ? java.util.List.of() : req.tiers())));
        } catch (PapelariaCatalogService.CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (PapelariaCatalogService.InvalidTierException e) {
            return error(400, "Bad Request", "invalid_tier");
        }
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/papelaria/catalog")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(
                companyId, user.userId(), req.name(), req.description(), req.priceCents(),
                req.category(), req.madeToOrder(), req.leadTimeDays(), req.specs()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        }
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/api/papelaria/catalog/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.name(), req.description(), req.priceCents(), req.category(), req.madeToOrder(),
                req.leadTimeDays(), req.clearLeadTime(), req.specs(), req.available()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    // ---- PATCH toggle -------------------------------------------------------
    @PatchMapping("/api/papelaria/catalog/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.available()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    // ---- DELETE -------------------------------------------------------------
    @DeleteMapping("/api/papelaria/catalog/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (CatalogItemInUseException e) {
            return error(409, "Conflict", "catalog_item_in_use");
        }
    }

    // ===== OPÇÕES ============================================================

    // ---- GET lista de opções ------------------------------------------------
    @GetMapping("/api/papelaria/catalog/{itemId}/options")
    public ResponseEntity<Object> listOptions(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("options", service.listOptions(companyId, itemId)));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    // ---- POST cria opção ----------------------------------------------------
    @PostMapping("/api/papelaria/catalog/{itemId}/options")
    public ResponseEntity<Object> createOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.addOption(companyId, user.userId(), itemId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        }
    }

    // ---- PATCH edita opção --------------------------------------------------
    @PatchMapping("/api/papelaria/catalog/{itemId}/options/{optionId}")
    public ResponseEntity<Object> updateOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId,
            @Valid @RequestBody UpdateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateOption(companyId, user.userId(), itemId, optionId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder(), req.available()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    // ---- PATCH toggle opção -------------------------------------------------
    @PatchMapping("/api/papelaria/catalog/{itemId}/options/{optionId}/toggle")
    public ResponseEntity<Object> toggleOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleOption(companyId, user.userId(), itemId, optionId, req.available()));
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    // ---- DELETE opção -------------------------------------------------------
    @DeleteMapping("/api/papelaria/catalog/{itemId}/options/{optionId}")
    public ResponseEntity<Object> deleteOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePapelaria(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteOption(companyId, user.userId(), itemId, optionId);
            return ResponseEntity.noContent().build();
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "catalog_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }
}
