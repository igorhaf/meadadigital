package com.meada.profiles.las.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.las.LasCategory;
import com.meada.profiles.las.LasMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo las (camada 8.23). Clone do
 * {@link com.meada.profiles.lingerie.catalog.LingerieProductService} (chassi de varejo) +
 * a gestão das VARIANTES (⭐ a grade COR × DYE_LOT — o eixo desta SM). DIFERENÇA: não há enum de
 * tamanho — {@code color} e {@code dyeLot} são TEXTO LIVRE, então não há {@code InvalidSizeException}
 * (a única validação de enum é a {@link LasCategory}). Valida categoria, audita as mutações e invalida
 * o {@link LasMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class LasProductService {

    private final LasProductRepository repository;
    private final LasVariantRepository variantRepository;
    private final AuditLogger auditLogger;
    private final LasMenuCache menuCache;
    private final com.meada.profiles.las.waitlist.LasWaitlistService waitlistService;

    public LasProductService(LasProductRepository repository,
                             LasVariantRepository variantRepository,
                             AuditLogger auditLogger,
                             LasMenuCache menuCache,
                             com.meada.profiles.las.waitlist.LasWaitlistService waitlistService) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
        this.waitlistService = waitlistService;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Produto não encontrado / de outro tenant (→ 404 product_not_found). */
    public static class ProductNotFoundException extends RuntimeException {}

    /** Produto com variante referenciada por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ProductInUseException extends RuntimeException {}

    /** Variante não encontrada / de outro produto-tenant (→ 404 variant_not_found). */
    public static class VariantNotFoundException extends RuntimeException {}

    /** Variante referenciada por order_item (FK restrict) — não pode hard-deletar (→ 409). */
    public static class VariantInUseException extends RuntimeException {}

    /** Variante duplicada (mesma combinação cor × dye_lot no produto) (→ 409 duplicate_variant). */
    public static class DuplicateVariantException extends RuntimeException {}

    // ---- Produtos -----------------------------------------------------------

    @Transactional
    public LasProduct create(UUID companyId, UUID userId, String name, String description,
                             String category, int basePriceCents) {
        requireValidCategory(category);
        LasProduct created = repository.insert(companyId, name, description, category, basePriceCents);
        auditLogger.log(companyId, userId, "las_product_created", "las_product",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public LasProduct update(UUID companyId, UUID userId, UUID id, String name, String description,
                             String category, Integer basePriceCents, Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        LasProduct updated = repository.update(companyId, id, name, description, category, basePriceCents, available)
            .orElseThrow(ProductNotFoundException::new);
        auditLogger.log(companyId, userId, "las_product_updated", "las_product", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public LasProduct toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        LasProduct product = repository.toggle(companyId, id, available)
            .orElseThrow(ProductNotFoundException::new);
        auditLogger.log(companyId, userId, "las_product_updated", "las_product", id,
            Map.of("available", available));
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
            // FK restrict: alguma variante deste produto está referenciada por um las_order_item.
            throw new ProductInUseException();
        }
        auditLogger.log(companyId, userId, "las_product_deleted", "las_product", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<LasProduct> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<LasProduct> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Variantes (⭐ a grade COR × DYE_LOT) --------------------------------

    public List<LasVariant> listVariants(UUID companyId, UUID productId) {
        requireProduct(companyId, productId);
        return variantRepository.listByProduct(companyId, productId);
    }

    @Transactional
    public LasVariant addVariant(UUID companyId, UUID userId, UUID productId, String color,
                                 String dyeLot, String sku, Integer priceCents, int stockQty) {
        requireProduct(companyId, productId);
        LasVariant created;
        try {
            created = variantRepository.insert(companyId, productId, color, dyeLot, sku, priceCents, stockQty);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(product_id, color, dye_lot) — combinação já existe nesse produto.
            throw new DuplicateVariantException();
        }
        auditLogger.log(companyId, userId, "las_variant_created", "las_variant",
            created.id(), Map.of("product_id", productId, "color", created.color(), "dye_lot", created.dyeLot()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public LasVariant updateVariant(UUID companyId, UUID userId, UUID productId, UUID variantId,
                                    String color, String dyeLot, String sku, Integer priceCents,
                                    Integer stockQty, Boolean available, boolean clearPrice) {
        requireProduct(companyId, productId);
        // Onda 1 (backlog #1): estoque anterior ANTES do update — reposição 0→N notifica a waitlist.
        Integer previousStock = variantRepository.findById(companyId, productId, variantId)
            .map(LasVariant::stockQty).orElse(null);
        LasVariant updated;
        try {
            updated = variantRepository.update(companyId, productId, variantId, color, dyeLot, sku,
                    priceCents, stockQty, available, clearPrice)
                .orElseThrow(VariantNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateVariantException();   // mexer em color/dye_lot pode colidir com o UNIQUE.
        }
        auditLogger.log(companyId, userId, "las_variant_updated", "las_variant", variantId, Map.of());
        menuCache.invalidate(companyId);
        if (previousStock != null && previousStock == 0 && updated.stockQty() > 0) {
            waitlistService.notifyBackInStock(companyId, variantId);
        }
        return updated;
    }

    @Transactional
    public LasVariant toggleVariant(UUID companyId, UUID userId, UUID productId, UUID variantId,
                                    boolean available) {
        requireProduct(companyId, productId);
        LasVariant variant = variantRepository.toggle(companyId, productId, variantId, available)
            .orElseThrow(VariantNotFoundException::new);
        auditLogger.log(companyId, userId, "las_variant_updated", "las_variant", variantId,
            Map.of("available", available));
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
            // FK restrict: existe las_order_item apontando para esta variante.
            throw new VariantInUseException();
        }
        auditLogger.log(companyId, userId, "las_variant_deleted", "las_variant", variantId, Map.of());
        menuCache.invalidate(companyId);
    }

    private void requireProduct(UUID companyId, UUID productId) {
        if (repository.findById(companyId, productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (LasCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
