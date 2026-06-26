package com.meada.whatsapp.profiles.las.catalog;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.las.LasProfileGuard;
import com.meada.whatsapp.profiles.las.LasProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.DuplicateVariantException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.InvalidCategoryException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.ProductInUseException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.ProductNotFoundException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.VariantInUseException;
import com.meada.whatsapp.profiles.las.catalog.LasProductService.VariantNotFoundException;
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
 * Catálogo do tenant las (camada 8.23). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProductController} (chassi de varejo) +
 * as rotas aninhadas de VARIANTE (⭐ a grade COR × DYE_LOT — o eixo desta SM). TENANT + perfil 'las'
 * only — {@link LasProfileGuard} rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob
 * {@code /api/las/products/**}. DIFERENÇA do lingerie: a variante carrega color + dyeLot (texto livre)
 * em vez de size + color; não há validação de enum de tamanho (sem invalid_size).
 */
@RestController
public class LasProductController {

    private final LasProductService service;
    private final LasProfileGuard profileGuard;

    public LasProductController(LasProductService service, LasProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação de produto. category validada contra o enum no service (400 invalid_category). */
    public record CreateProductRequest(
        @NotBlank @Size(max = 200) String name,
        String description,
        @NotBlank String category,
        @PositiveOrZero int basePriceCents) {}

    /** Body de edição de produto (PATCH parcial; todos opcionais). */
    public record UpdateProductRequest(
        @Size(max = 200) String name,
        String description,
        String category,
        @PositiveOrZero Integer basePriceCents,
        Boolean available) {}

    public record ToggleRequest(boolean available) {}

    /** Body de criação de variante (⭐ COR × DYE_LOT). priceCents nullable = herda o base do produto. */
    public record CreateVariantRequest(
        @NotBlank @Size(max = 40) String color,
        @NotBlank @Size(max = 40) String dyeLot,
        @Size(max = 80) String sku,
        @PositiveOrZero Integer priceCents,
        @PositiveOrZero int stockQty) {}

    /**
     * Body de edição de variante (PATCH parcial; todos opcionais). {@code clearPrice=true} limpa o
     * priceCents (volta a herdar o base do produto).
     */
    public record UpdateVariantRequest(
        @Size(max = 40) String color,
        @Size(max = 40) String dyeLot,
        @Size(max = 80) String sku,
        @PositiveOrZero Integer priceCents,
        @PositiveOrZero Integer stockQty,
        Boolean available,
        boolean clearPrice) {}

    // ===== PRODUTOS ==========================================================

    // ---- GET lista ----------------------------------------------------------
    @GetMapping("/api/las/products")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean available) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, available)));
    }

    // ---- GET detalhe (com variantes) ----------------------------------------
    @GetMapping("/api/las/products/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "product_not_found"));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/las/products")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateProductRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(
                companyId, user.userId(), req.name(), req.description(), req.category(), req.basePriceCents()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        }
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/api/las/products/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.name(), req.description(), req.category(), req.basePriceCents(), req.available()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        }
    }

    // ---- PATCH toggle -------------------------------------------------------
    @PatchMapping("/api/las/products/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.available()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        }
    }

    // ---- DELETE -------------------------------------------------------------
    @DeleteMapping("/api/las/products/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (ProductInUseException e) {
            return error(409, "Conflict", "product_in_use");
        }
    }

    // ===== VARIANTES (⭐ a grade COR × DYE_LOT) ===============================

    // ---- GET lista de variantes ---------------------------------------------
    @GetMapping("/api/las/products/{productId}/variants")
    public ResponseEntity<Object> listVariants(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(Map.of("variants", service.listVariants(companyId, productId)));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        }
    }

    // ---- POST cria variante -------------------------------------------------
    @PostMapping("/api/las/products/{productId}/variants")
    public ResponseEntity<Object> createVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateVariantRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.addVariant(companyId, user.userId(), productId,
                req.color(), req.dyeLot(), req.sku(), req.priceCents(), req.stockQty()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (DuplicateVariantException e) {
            return error(409, "Conflict", "duplicate_variant");
        }
    }

    // ---- PATCH edita variante -----------------------------------------------
    @PatchMapping("/api/las/products/{productId}/variants/{variantId}")
    public ResponseEntity<Object> updateVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateVariant(companyId, user.userId(), productId, variantId,
                req.color(), req.dyeLot(), req.sku(), req.priceCents(), req.stockQty(), req.available(), req.clearPrice()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (VariantNotFoundException e) {
            return error(404, "Not Found", "variant_not_found");
        } catch (DuplicateVariantException e) {
            return error(409, "Conflict", "duplicate_variant");
        }
    }

    // ---- PATCH toggle variante ----------------------------------------------
    @PatchMapping("/api/las/products/{productId}/variants/{variantId}/toggle")
    public ResponseEntity<Object> toggleVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleVariant(companyId, user.userId(), productId, variantId, req.available()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (VariantNotFoundException e) {
            return error(404, "Not Found", "variant_not_found");
        }
    }

    // ---- DELETE variante ----------------------------------------------------
    @DeleteMapping("/api/las/products/{productId}/variants/{variantId}")
    public ResponseEntity<Object> deleteVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireLas(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.deleteVariant(companyId, user.userId(), productId, variantId);
            return ResponseEntity.noContent().build();
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (VariantNotFoundException e) {
            return error(404, "Not Found", "variant_not_found");
        } catch (VariantInUseException e) {
            return error(409, "Conflict", "variant_in_use");
        }
    }
}
