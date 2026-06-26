package com.meada.whatsapp.profiles.suplementos.catalog;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.suplementos.SuplementosProfileGuard;
import com.meada.whatsapp.profiles.suplementos.SuplementosProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.DuplicateVariantException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.InvalidCategoryException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.ProductInUseException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.ProductNotFoundException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.VariantInUseException;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService.VariantNotFoundException;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Catálogo do tenant suplementos (camada 8.24). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProductController} (chassi de varejo) +
 * as rotas aninhadas de VARIANTE (⭐ a grade sabor×peso). TENANT + perfil 'suplementos' only —
 * {@link SuplementosProfileGuard} rejeita 403 forbidden_wrong_profile qualquer outro perfil. Sob
 * {@code /api/suplementos/products/**}.
 */
@RestController
public class SupProductController {

    private final SupProductService service;
    private final SuplementosProfileGuard profileGuard;

    public SupProductController(SupProductService service, SuplementosProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /** Body de criação de produto. category validada contra o enum no service (400 invalid_category). */
    public record CreateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 120) String brand,
        @NotBlank String category,
        String description) {}

    /** Body de edição de produto (PATCH parcial; todos opcionais). */
    public record UpdateProductRequest(
        @Size(max = 200) String name,
        @Size(max = 120) String brand,
        String category,
        String description,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    /** Body de criação de variante (⭐ sabor×peso). flavor nullable (acessório sem sabor). */
    public record CreateVariantRequest(
        @Size(max = 60) String flavor,
        @NotBlank @Size(max = 60) String sizeLabel,
        @Size(max = 80) String sku,
        @PositiveOrZero int priceCents,
        @PositiveOrZero int stockQuantity,
        LocalDate expiryDate) {}

    /**
     * Body de edição de variante (PATCH parcial; todos opcionais). {@code clearExpiry=true} limpa a
     * validade; {@code clearFlavor=true} limpa o sabor.
     */
    public record UpdateVariantRequest(
        @Size(max = 60) String flavor,
        @Size(max = 60) String sizeLabel,
        @Size(max = 80) String sku,
        @PositiveOrZero Integer priceCents,
        @PositiveOrZero Integer stockQuantity,
        LocalDate expiryDate,
        boolean clearExpiry,
        Boolean active,
        boolean clearFlavor) {}

    // ===== PRODUTOS ==========================================================

    // ---- GET lista ----------------------------------------------------------
    @GetMapping("/api/suplementos/products")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean active) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, category, active)));
    }

    // ---- GET detalhe (com variantes) ----------------------------------------
    @GetMapping("/api/suplementos/products/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "product_not_found"));
    }

    // ---- POST cria ----------------------------------------------------------
    @PostMapping("/api/suplementos/products")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateProductRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(
                companyId, user.userId(), req.name(), req.brand(), req.category(), req.description()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        }
    }

    // ---- PATCH edita --------------------------------------------------------
    @PatchMapping("/api/suplementos/products/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id,
                req.name(), req.brand(), req.category(), req.description(), req.active()));
        } catch (InvalidCategoryException e) {
            return error(400, "Bad Request", "invalid_category");
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        }
    }

    // ---- PATCH toggle -------------------------------------------------------
    @PatchMapping("/api/suplementos/products/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        }
    }

    // ---- DELETE -------------------------------------------------------------
    @DeleteMapping("/api/suplementos/products/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
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

    // ===== VARIANTES (⭐ a grade sabor×peso) ==================================

    // ---- GET lista de variantes ---------------------------------------------
    @GetMapping("/api/suplementos/products/{productId}/variants")
    public ResponseEntity<Object> listVariants(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
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
    @PostMapping("/api/suplementos/products/{productId}/variants")
    public ResponseEntity<Object> createVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @Valid @RequestBody CreateVariantRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.addVariant(companyId, user.userId(), productId,
                req.flavor(), req.sizeLabel(), req.sku(), req.priceCents(), req.stockQuantity(), req.expiryDate()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (DuplicateVariantException e) {
            return error(409, "Conflict", "duplicate_variant");
        }
    }

    // ---- PATCH edita variante -----------------------------------------------
    @PatchMapping("/api/suplementos/products/{productId}/variants/{variantId}")
    public ResponseEntity<Object> updateVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @Valid @RequestBody UpdateVariantRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.updateVariant(companyId, user.userId(), productId, variantId,
                req.flavor(), req.sizeLabel(), req.sku(), req.priceCents(), req.stockQuantity(),
                req.expiryDate(), req.clearExpiry(), req.active(), req.clearFlavor()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (VariantNotFoundException e) {
            return error(404, "Not Found", "variant_not_found");
        } catch (DuplicateVariantException e) {
            return error(409, "Conflict", "duplicate_variant");
        }
    }

    // ---- PATCH toggle variante ----------------------------------------------
    @PatchMapping("/api/suplementos/products/{productId}/variants/{variantId}/toggle")
    public ResponseEntity<Object> toggleVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @PathVariable UUID variantId,
            @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggleVariant(companyId, user.userId(), productId, variantId, req.active()));
        } catch (ProductNotFoundException e) {
            return error(404, "Not Found", "product_not_found");
        } catch (VariantNotFoundException e) {
            return error(404, "Not Found", "variant_not_found");
        }
    }

    // ---- DELETE variante ----------------------------------------------------
    @DeleteMapping("/api/suplementos/products/{productId}/variants/{variantId}")
    public ResponseEntity<Object> deleteVariant(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID productId,
            @PathVariable UUID variantId) {
        UUID companyId;
        try {
            companyId = profileGuard.requireSuplementos(user);
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
