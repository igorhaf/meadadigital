package com.meada.profiles.oficina.catalog;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.oficina.OficinaProfileGuard;
import com.meada.profiles.oficina.OficinaProfileGuard.WrongProfileException;
import com.meada.profiles.oficina.catalog.OficinaCatalogService.CatalogItemNotFoundException;
import com.meada.profiles.oficina.catalog.OficinaCatalogService.InvalidCatalogItemException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * Catálogo de materiais/técnicas do tenant oficina (onda 2, backlog #15). TENANT + perfil 'oficina'
 * only. CRUD; o editor de orçamento usa a lista como autofill (o item da proposta é snapshot texto).
 */
@RestController
public class OficinaCatalogController {

    private final OficinaCatalogService service;
    private final OficinaProfileGuard profileGuard;

    public OficinaCatalogController(OficinaCatalogService service, OficinaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String name,
        String category,
        @NotNull Integer unitPriceCents,
        Boolean active,
        String notes) {}

    public record UpdateRequest(
        @Size(max = 200) String name,
        String category,
        Boolean clearCategory,
        Integer unitPriceCents,
        Boolean active,
        String notes,
        Boolean clearNotes) {}

    @GetMapping("/api/oficina/catalog")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @PostMapping("/api/oficina/catalog")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            String category = req.category() == null || req.category().isBlank() ? null : req.category().trim();
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                category, req.unitPriceCents(), req.active(), req.notes()));
        } catch (InvalidCatalogItemException e) {
            return error(400, "Bad Request", "invalid_item");
        }
    }

    @PatchMapping("/api/oficina/catalog/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean categoryProvided = req.category() != null || Boolean.TRUE.equals(req.clearCategory());
        String category = Boolean.TRUE.equals(req.clearCategory()) ? null
            : (req.category() == null || req.category().isBlank() ? null : req.category().trim());
        boolean notesProvided = req.notes() != null || Boolean.TRUE.equals(req.clearNotes());
        String notes = Boolean.TRUE.equals(req.clearNotes()) ? null : req.notes();
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), category,
                categoryProvided, req.unitPriceCents(), req.active(), notes, notesProvided));
        } catch (InvalidCatalogItemException e) {
            return error(400, "Bad Request", "invalid_item");
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        }
    }

    @DeleteMapping("/api/oficina/catalog/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireOficina(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (CatalogItemNotFoundException e) {
            return error(404, "Not Found", "item_not_found");
        }
    }
}
