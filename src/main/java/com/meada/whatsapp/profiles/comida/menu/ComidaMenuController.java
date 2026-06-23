package com.meada.whatsapp.profiles.comida.menu;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.comida.ComidaProfileGuard;
import com.meada.whatsapp.profiles.comida.ComidaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuService.InvalidCategoryException;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuService.MenuItemInUseException;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuService.MenuItemNotFoundException;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuService.OptionNotFoundException;
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
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Cardápio do tenant comida (camada 8.4). Clone de
 * {@link com.meada.whatsapp.profiles.sushi.menu.SushiMenuController} + as rotas aninhadas de OPÇÃO
 * (ESCAPADA 2). TENANT + perfil 'comida' only — {@link ComidaProfileGuard} rejeita 403
 * forbidden_wrong_profile qualquer outro perfil. Sob {@code /api/comida/menu/**}.
 */
@RestController
public class ComidaMenuController {

    private final ComidaMenuService service;
    private final ComidaProfileGuard profileGuard;

    public ComidaMenuController(ComidaMenuService service, ComidaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação de item. category validada contra o enum no service (400 invalid_category). */
    public record CreateMenuItemRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        @PositiveOrZero int priceCents,
        @NotBlank String category) {}

    /** Body de edição de item (PATCH parcial; todos opcionais). */
    public record UpdateMenuItemRequest(
        @Size(max = 120) String name,
        String description,
        @PositiveOrZero Integer priceCents,
        String category,
        Boolean available) {}

    public record ToggleRequest(boolean available) {}

    /** Body de criação de opção (ESCAPADA 2). */
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
    @GetMapping("/api/comida/menu")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, available)));
    }

    // ---- GET detalhe (com options) ------------------------------------------
    @GetMapping("/api/comida/menu/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "menu_item_not_found"));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/comida/menu")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateMenuItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(
                companyId, user.userId(), req.name(), req.description(), req.priceCents(), req.category()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        }
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/api/comida/menu/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMenuItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.name(), req.description(), req.priceCents(), req.category(), req.available()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        }
    }

    // ---- PATCH toggle -------------------------------------------------------
    @PatchMapping("/api/comida/menu/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.available()));
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        }
    }

    // ---- DELETE -------------------------------------------------------------
    @DeleteMapping("/api/comida/menu/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        } catch (MenuItemInUseException e) {
            return error(409, "Conflict", "menu_item_in_use");
        }
    }

    // ===== OPÇÕES (ESCAPADA 2) ===============================================

    // ---- GET lista de opções ------------------------------------------------
    @GetMapping("/api/comida/menu/{itemId}/options")
    public ResponseEntity<Object> listOptions(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("options", service.listOptions(companyId, itemId)));
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        }
    }

    // ---- POST cria opção ----------------------------------------------------
    @PostMapping("/api/comida/menu/{itemId}/options")
    public ResponseEntity<Object> createOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @Valid @RequestBody CreateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.addOption(companyId, user.userId(), itemId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder()));
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        }
    }

    // ---- PATCH edita opção --------------------------------------------------
    @PatchMapping("/api/comida/menu/{itemId}/options/{optionId}")
    public ResponseEntity<Object> updateOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId,
            @Valid @RequestBody UpdateOptionRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateOption(companyId, user.userId(), itemId, optionId,
                req.groupLabel(), req.optionLabel(), req.priceDeltaCents(), req.sortOrder(), req.available()));
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    // ---- PATCH toggle opção -------------------------------------------------
    @PatchMapping("/api/comida/menu/{itemId}/options/{optionId}/toggle")
    public ResponseEntity<Object> toggleOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleOption(companyId, user.userId(), itemId, optionId, req.available()));
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }

    // ---- DELETE opção -------------------------------------------------------
    @DeleteMapping("/api/comida/menu/{itemId}/options/{optionId}")
    public ResponseEntity<Object> deleteOption(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID itemId,
            @PathVariable UUID optionId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireComida(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteOption(companyId, user.userId(), itemId, optionId);
            return ResponseEntity.noContent().build();
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        } catch (OptionNotFoundException e) {
            return error(404, "Not Found", "option_not_found");
        }
    }
}
