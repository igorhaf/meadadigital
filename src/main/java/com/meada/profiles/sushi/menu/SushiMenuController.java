package com.meada.profiles.sushi.menu;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.sushi.SushiProfileGuard;
import com.meada.profiles.sushi.SushiProfileGuard.WrongProfileException;
import com.meada.profiles.sushi.menu.SushiMenuService.InvalidCategoryException;
import com.meada.profiles.sushi.menu.SushiMenuService.MenuItemInUseException;
import com.meada.profiles.sushi.menu.SushiMenuService.MenuItemNotFoundException;
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
 * Cardápio do tenant sushi (camada 7.1). TENANT + perfil 'sushi' only — {@link SushiProfileGuard}
 * rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob {@code /api/sushi/**} (rota de
 * tenant fora do prefixo /admin, com CORS já liberado na fase 0.5).
 */
@RestController
public class SushiMenuController {

    private final SushiMenuService service;
    private final SushiProfileGuard profileGuard;

    public SushiMenuController(SushiMenuService service, SushiProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /**
     * Body de criação. category é o UUID de uma categoria do tenant — OPCIONAL agora (item sem
     * categoria é permitido). Validada contra a tabela sushi_categories no service (400 invalid_category).
     */
    public record CreateMenuItemRequest(
        @NotBlank @Size(max = 120) String name,
        String description,
        @PositiveOrZero int priceCents,
        String category) {}

    /**
     * Body de edição (PATCH parcial; todos opcionais). {@code clearCategory=true} limpa a categoria
     * (item sem categoria). category presente seta; ambos ausentes = não mexe.
     */
    public record UpdateMenuItemRequest(
        @Size(max = 120) String name,
        String description,
        @PositiveOrZero Integer priceCents,
        String category,
        Boolean clearCategory,
        Boolean available) {}

    public record ToggleRequest(boolean available) {}

    // ---- GET lista ----------------------------------------------------------
    @GetMapping("/api/sushi/menu")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, available)));
    }

    // ---- GET detalhe --------------------------------------------------------
    @GetMapping("/api/sushi/menu/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "menu_item_not_found"));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/sushi/menu")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateMenuItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
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
    @PatchMapping("/api/sushi/menu/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMenuItemRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean categoryProvided = req.category() != null || Boolean.TRUE.equals(req.clearCategory());
        String category = Boolean.TRUE.equals(req.clearCategory()) ? null : req.category();
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.name(), req.description(), req.priceCents(), category, categoryProvided, req.available()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (MenuItemNotFoundException e) {
            return error(404, "Not Found", "menu_item_not_found");
        }
    }

    // ---- PATCH toggle -------------------------------------------------------
    @PatchMapping("/api/sushi/menu/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
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
    @DeleteMapping("/api/sushi/menu/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
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
}
