package com.meada.profiles.sushi.categories;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.sushi.SushiProfileGuard;
import com.meada.profiles.sushi.SushiProfileGuard.WrongProfileException;
import com.meada.profiles.sushi.categories.SushiCategoryService.CategoryInUseException;
import com.meada.profiles.sushi.categories.SushiCategoryService.CategoryNotFoundException;
import com.meada.profiles.sushi.categories.SushiCategoryService.DuplicateCategoryException;
import com.meada.profiles.sushi.categories.SushiCategoryService.InvalidCategoryNameException;
import jakarta.validation.Valid;
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

import java.util.Map;
import java.util.UUID;

/**
 * Categorias do cardápio do tenant sushi (camada 7.1 / sushi funcional). TENANT + perfil 'sushi'
 * only — {@link SushiProfileGuard} rejeita 403 forbidden_wrong_profile qualquer outro perfil.
 * Sob {@code /api/sushi/**} (já autenticado pelo JwtAuthenticationFilter).
 */
@RestController
public class SushiCategoryController {

    private final SushiCategoryService service;
    private final SushiProfileGuard profileGuard;

    public SushiCategoryController(SushiCategoryService service, SushiProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 80) String name,
        Integer sortOrder,
        Boolean active) {}

    public record UpdateRequest(
        @Size(max = 80) String name,
        Integer sortOrder,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/sushi/categories")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @PostMapping("/api/sushi/categories")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(
                companyId, user.userId(), req.name(), req.sortOrder(), req.active()));
        } catch (InvalidCategoryNameException e) {
            return error(400, "Bad Request", "invalid_category_name");
        } catch (DuplicateCategoryException e) {
            return error(409, "Conflict", "duplicate_category");
        }
    }

    @PatchMapping("/api/sushi/categories/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(
                companyId, user.userId(), id, req.name(), req.sortOrder(), req.active()));
        } catch (InvalidCategoryNameException e) {
            return error(400, "Bad Request", "invalid_category_name");
        } catch (CategoryNotFoundException e) {
            return error(404, "Not Found", "category_not_found");
        } catch (DuplicateCategoryException e) {
            return error(409, "Conflict", "duplicate_category");
        }
    }

    @PatchMapping("/api/sushi/categories/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSushi(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (CategoryNotFoundException e) {
            return error(404, "Not Found", "category_not_found");
        }
    }

    @DeleteMapping("/api/sushi/categories/{id}")
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
        } catch (CategoryNotFoundException e) {
            return error(404, "Not Found", "category_not_found");
        } catch (CategoryInUseException e) {
            return error(409, "Conflict", "category_in_use");
        }
    }
}
