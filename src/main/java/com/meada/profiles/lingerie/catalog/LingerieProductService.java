package com.meada.profiles.lingerie.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.lingerie.LingerieCategory;
import com.meada.profiles.lingerie.LingerieMenuCache;
import com.meada.profiles.lingerie.LingerieSize;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo lingerie (camada 8.21). Análogo ao
 * {@link com.meada.profiles.adega.menu.AdegaMenuService} + a gestão das VARIANTES (⭐ a grade
 * tamanho×cor — substitui as opções/modifiers do adega). Valida categoria contra
 * {@link LingerieCategory} e tamanho contra {@link LingerieSize}, audita as mutações e invalida o
 * {@link LingerieMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class LingerieProductService {

    private final LingerieProductRepository repository;
    private final LingerieVariantRepository variantRepository;
    private final AuditLogger auditLogger;
    private final LingerieMenuCache menuCache;
    private final com.meada.profiles.lingerie.alerts.LingerieStockAlertService stockAlertService;

    public LingerieProductService(LingerieProductRepository repository,
                                  LingerieVariantRepository variantRepository,
                                  AuditLogger auditLogger,
                                  LingerieMenuCache menuCache,
                                  com.meada.profiles.lingerie.alerts.LingerieStockAlertService stockAlertService) {
        this.repository = repository;
        this.variantRepository = variantRepository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
        this.stockAlertService = stockAlertService;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Tamanho inválido (→ 400 invalid_size no controller). */
    public static class InvalidSizeException extends RuntimeException {}

    /** Produto não encontrado / de outro tenant (→ 404 product_not_found). */
    public static class ProductNotFoundException extends RuntimeException {}

    /** Produto com variante referenciada por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ProductInUseException extends RuntimeException {}

    /** Variante não encontrada / de outro produto-tenant (→ 404 variant_not_found). */
    public static class VariantNotFoundException extends RuntimeException {}

    /** Variante referenciada por order_item (FK restrict) — não pode hard-deletar (→ 409). */
    public static class VariantInUseException extends RuntimeException {}

    /** Variante duplicada (mesma combinação tamanho×cor no produto) (→ 409 duplicate_variant). */
    public static class DuplicateVariantException extends RuntimeException {}

    // ---- Produtos -----------------------------------------------------------

    @Transactional
    public LingerieProduct create(UUID companyId, UUID userId, String name, String description,
                                  String category, int basePriceCents) {
        requireValidCategory(category);
        LingerieProduct created = repository.insert(companyId, name, description, category, basePriceCents);
        auditLogger.log(companyId, userId, "lingerie_product_created", "lingerie_product",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public LingerieProduct update(UUID companyId, UUID userId, UUID id, String name, String description,
                                  String category, Integer basePriceCents, Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        LingerieProduct updated = repository.update(companyId, id, name, description, category, basePriceCents, available)
            .orElseThrow(ProductNotFoundException::new);
        auditLogger.log(companyId, userId, "lingerie_product_updated", "lingerie_product", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public LingerieProduct toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        LingerieProduct product = repository.toggle(companyId, id, available)
            .orElseThrow(ProductNotFoundException::new);
        auditLogger.log(companyId, userId, "lingerie_product_updated", "lingerie_product", id,
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
            // FK restrict: alguma variante deste produto está referenciada por um lingerie_order_item.
            throw new ProductInUseException();
        }
        auditLogger.log(companyId, userId, "lingerie_product_deleted", "lingerie_product", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<LingerieProduct> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<LingerieProduct> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Variantes (⭐ a grade tamanho×cor) ----------------------------------

    public List<LingerieVariant> listVariants(UUID companyId, UUID productId) {
        requireProduct(companyId, productId);
        return variantRepository.listByProduct(companyId, productId);
    }

    @Transactional
    public LingerieVariant addVariant(UUID companyId, UUID userId, UUID productId, String size,
                                      String color, String sku, Integer priceCents, int stockQty) {
        requireProduct(companyId, productId);
        requireValidSize(size);
        LingerieVariant created;
        try {
            created = variantRepository.insert(companyId, productId, size, color, sku, priceCents, stockQty);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(product_id, size, color) — combinação já existe nesse produto.
            throw new DuplicateVariantException();
        }
        auditLogger.log(companyId, userId, "lingerie_variant_created", "lingerie_variant",
            created.id(), Map.of("product_id", productId, "size", created.size(), "color", created.color()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public LingerieVariant updateVariant(UUID companyId, UUID userId, UUID productId, UUID variantId,
                                         String size, String color, String sku, Integer priceCents,
                                         Integer stockQty, Boolean available, boolean clearPrice) {
        requireProduct(companyId, productId);
        // Onda 1 (avise-me): captura o estoque ANTES pra detectar reposição 0 → N.
        Integer previousStock = variantRepository.findById(companyId, productId, variantId)
            .map(LingerieVariant::stockQty).orElse(null);
        if (size != null && !size.isBlank()) {
            requireValidSize(size);
        }
        LingerieVariant updated;
        try {
            updated = variantRepository.update(companyId, productId, variantId, size, color, sku,
                    priceCents, stockQty, available, clearPrice)
                .orElseThrow(VariantNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateVariantException();   // mexer em size/color pode colidir com o UNIQUE.
        }
        if (previousStock != null && previousStock == 0 && updated.stockQty() > 0) {
            stockAlertService.notifyBackInStock(companyId, variantId);
        }
        auditLogger.log(companyId, userId, "lingerie_variant_updated", "lingerie_variant", variantId, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public LingerieVariant toggleVariant(UUID companyId, UUID userId, UUID productId, UUID variantId,
                                         boolean available) {
        requireProduct(companyId, productId);
        LingerieVariant variant = variantRepository.toggle(companyId, productId, variantId, available)
            .orElseThrow(VariantNotFoundException::new);
        auditLogger.log(companyId, userId, "lingerie_variant_updated", "lingerie_variant", variantId,
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
            // FK restrict: existe lingerie_order_item apontando para esta variante.
            throw new VariantInUseException();
        }
        auditLogger.log(companyId, userId, "lingerie_variant_deleted", "lingerie_variant", variantId, Map.of());
        menuCache.invalidate(companyId);
    }

    private void requireProduct(UUID companyId, UUID productId) {
        if (repository.findById(companyId, productId).isEmpty()) {
            throw new ProductNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (LingerieCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }

    private void requireValidSize(String size) {
        if (LingerieSize.fromId(size).isEmpty()) {
            throw new InvalidSizeException();
        }
    }
}
