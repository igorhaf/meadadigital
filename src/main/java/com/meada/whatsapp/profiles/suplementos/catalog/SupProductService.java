package com.meada.whatsapp.profiles.suplementos.catalog;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.suplementos.SuplementosCategory;
import com.meada.whatsapp.profiles.suplementos.SuplementosMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo suplementos (camada 8.24). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProductService} (chassi de varejo) + a
 * gestão das VARIANTES (⭐ a grade sabor×peso). Valida categoria contra {@link SuplementosCategory}
 * (NÃO há enum de tamanho — flavor/sizeLabel são texto livre, ≠ lingerie que validava size), audita as
 * mutações e invalida o {@link SuplementosMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class SupProductService {

    private final SupProductRepository repository;
    private final SupVariantRepository variantRepository;
    private final AuditLogger auditLogger;
    private final SuplementosMenuCache menuCache;

    public SupProductService(SupProductRepository repository,
                             SupVariantRepository variantRepository,
                             AuditLogger auditLogger,
                             SuplementosMenuCache menuCache) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Produto não encontrado / de outro tenant (→ 404 product_not_found). */
    public static class ProductNotFoundException extends RuntimeException {}

    /** Produto com variante referenciada por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ProductInUseException extends RuntimeException {}

    /** Variante não encontrada / de outro produto-tenant (→ 404 variant_not_found). */
    public static class VariantNotFoundException extends RuntimeException {}

    /** Variante referenciada por sup_order_item (FK restrict) — não pode hard-deletar (→ 409). */
    public static class VariantInUseException extends RuntimeException {}

    /** SKU duplicado (UNIQUE(company_id, sku) onde sku not null) (→ 409 duplicate_variant). */
    public static class DuplicateVariantException extends RuntimeException {}

    // ---- Produtos -----------------------------------------------------------

    @Transactional
    public SupProduct create(UUID companyId, UUID userId, String name, String brand,
                             String category, String description) {
        requireValidCategory(category);
        SupProduct created = repository.insert(companyId, name, brand, category, description);
        auditLogger.log(companyId, userId, "sup_product_created", "sup_product",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public SupProduct update(UUID companyId, UUID userId, UUID id, String name, String brand,
                             String category, String description, Boolean active) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        SupProduct updated = repository.update(companyId, id, name, brand, category, description, active)
            .orElseThrow(ProductNotFoundException::new);
        auditLogger.log(companyId, userId, "sup_product_updated", "sup_product", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public SupProduct toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        SupProduct product = repository.toggle(companyId, id, active)
            .orElseThrow(ProductNotFoundException::new);
        auditLogger.log(companyId, userId, "sup_product_updated", "sup_product", id,
            Map.of("active", active));
        menuCache.invalidate(companyId);
        return product;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new ProductNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: alguma variante deste produto está referenciada por um sup_order_item.
            throw new ProductInUseException();
        }
        auditLogger.log(companyId, userId, "sup_product_deleted", "sup_product", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<SupProduct> list(UUID companyId, String category, boolean onlyActive) {
        return repository.listByCompany(companyId, category, onlyActive);
    }

    public Optional<SupProduct> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Variantes (⭐ a grade sabor×peso) -----------------------------------

    public List<SupVariant> listVariants(UUID companyId, UUID productId) {
        requireProduct(companyId, productId);
        return variantRepository.listByProduct(companyId, productId);
    }

    @Transactional
    public SupVariant addVariant(UUID companyId, UUID userId, UUID productId, String flavor,
                                 String sizeLabel, String sku, int priceCents, int stockQuantity,
                                 LocalDate expiryDate) {
        requireProduct(companyId, productId);
        SupVariant created;
        try {
            created = variantRepository.insert(companyId, productId, flavor, sizeLabel, sku, priceCents,
                stockQuantity, expiryDate);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(company_id, sku) where sku not null — SKU já existe nesse tenant.
            throw new DuplicateVariantException();
        }
        auditLogger.log(companyId, userId, "sup_variant_created", "sup_variant",
            created.id(), Map.of("product_id", productId, "size_label", created.sizeLabel()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public SupVariant updateVariant(UUID companyId, UUID userId, UUID productId, UUID variantId,
                                    String flavor, String sizeLabel, String sku, Integer priceCents,
                                    Integer stockQuantity, LocalDate expiryDate, boolean clearExpiry,
                                    Boolean active, boolean clearFlavor) {
        requireProduct(companyId, productId);
        SupVariant updated;
        try {
            updated = variantRepository.update(companyId, productId, variantId, flavor, sizeLabel, sku,
                    priceCents, stockQuantity, expiryDate, clearExpiry, active, clearFlavor)
                .orElseThrow(VariantNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateVariantException();   // mexer no sku pode colidir com o UNIQUE.
        }
        auditLogger.log(companyId, userId, "sup_variant_updated", "sup_variant", variantId, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public SupVariant toggleVariant(UUID companyId, UUID userId, UUID productId, UUID variantId,
                                    boolean active) {
        requireProduct(companyId, productId);
        SupVariant variant = variantRepository.toggle(companyId, productId, variantId, active)
            .orElseThrow(VariantNotFoundException::new);
        auditLogger.log(companyId, userId, "sup_variant_updated", "sup_variant", variantId,
            Map.of("active", active));
        menuCache.invalidate(companyId);
        return variant;
    }

    @Transactional
    public void deleteVariant(UUID companyId, UUID userId, UUID productId, UUID variantId) {
        requireProduct(companyId, productId);
        try {
            boolean deleted = variantRepository.delete(companyId, productId, variantId);
            if (!deleted) {
                throw new VariantNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe sup_order_item apontando para esta variante.
            throw new VariantInUseException();
        }
        auditLogger.log(companyId, userId, "sup_variant_deleted", "sup_variant", variantId, Map.of());
        menuCache.invalidate(companyId);
    }

    private void requireProduct(UUID companyId, UUID productId) {
        if (repository.findById(companyId, productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (SuplementosCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
