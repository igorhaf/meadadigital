package com.meada.profiles.floricultura.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.floricultura.FloriculturaCategory;
import com.meada.profiles.floricultura.FloriculturaCatalogCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do cardápio do floricultura (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.catalog.SushiCatalogService} + a gestão das OPÇÕES (ESCAPADA 2).
 * Valida categoria contra {@link FloriculturaCategory}, audita as mutações (audit_log do tenant) e invalida
 * o {@link FloriculturaCatalogCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class FloriculturaCatalogService {

    private final FloriculturaCatalogItemRepository repository;
    private final FloriculturaCatalogOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final FloriculturaCatalogCache catalogCache;

    public FloriculturaCatalogService(FloriculturaCatalogItemRepository repository,
                             FloriculturaCatalogOptionRepository optionRepository,
                             AuditLogger auditLogger,
                             FloriculturaCatalogCache catalogCache) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Item não encontrado / de outro tenant (→ 404). */
    public static class CatalogItemNotFoundException extends RuntimeException {}

    /** Item referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class CatalogItemInUseException extends RuntimeException {}

    /** Opção não encontrada / de outro item-tenant (→ 404). */
    public static class OptionNotFoundException extends RuntimeException {}

    // ---- Itens --------------------------------------------------------------

    @Transactional
    public FloriculturaCatalogItem create(UUID companyId, UUID userId, String name, String description,
                                 int priceCents, String category, boolean suggestible) {
        requireValidCategory(category);
        FloriculturaCatalogItem created = repository.insert(companyId, name, description, priceCents, category, suggestible);
        auditLogger.log(companyId, userId, "floricultura_catalog_item_created", "floricultura_catalog_item",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        catalogCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public FloriculturaCatalogItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                 Integer priceCents, String category, Boolean available,
                                 Boolean suggestible) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        FloriculturaCatalogItem updated = repository.update(companyId, id, name, description, priceCents,
                category, available, suggestible)
            .orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "floricultura_catalog_item_updated", "floricultura_catalog_item", id, Map.of());
        catalogCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public FloriculturaCatalogItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        FloriculturaCatalogItem item = repository.toggle(companyId, id, available)
            .orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "floricultura_catalog_item_updated", "floricultura_catalog_item", id,
            Map.of("available", available));
        catalogCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new CatalogItemNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe floricultura_order_item apontando para este item.
            throw new CatalogItemInUseException();
        }
        auditLogger.log(companyId, userId, "floricultura_catalog_item_deleted", "floricultura_catalog_item", id, Map.of());
        catalogCache.invalidate(companyId);
    }

    public List<FloriculturaCatalogItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<FloriculturaCatalogItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    public List<FloriculturaCatalogOption> listOptions(UUID companyId, UUID catalogItemId) {
        requireItem(companyId, catalogItemId);
        return optionRepository.listByItem(companyId, catalogItemId);
    }

    @Transactional
    public FloriculturaCatalogOption addOption(UUID companyId, UUID userId, UUID catalogItemId, String groupLabel,
                                      String optionLabel, int priceDeltaCents, int sortOrder) {
        requireItem(companyId, catalogItemId);
        FloriculturaCatalogOption created = optionRepository.insert(
            companyId, catalogItemId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "floricultura_catalog_option_created", "floricultura_catalog_item_option",
            created.id(), Map.of("catalog_item_id", catalogItemId, "group_label", created.groupLabel()));
        catalogCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public FloriculturaCatalogOption updateOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId,
                                         String groupLabel, String optionLabel, Integer priceDeltaCents,
                                         Integer sortOrder, Boolean available) {
        requireItem(companyId, catalogItemId);
        FloriculturaCatalogOption updated = optionRepository.update(companyId, catalogItemId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "floricultura_catalog_option_updated", "floricultura_catalog_item_option",
            optionId, Map.of());
        catalogCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public FloriculturaCatalogOption toggleOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId,
                                         boolean available) {
        requireItem(companyId, catalogItemId);
        FloriculturaCatalogOption option = optionRepository.toggle(companyId, catalogItemId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "floricultura_catalog_option_updated", "floricultura_catalog_item_option",
            optionId, Map.of("available", available));
        catalogCache.invalidate(companyId);
        return option;
    }

    @Transactional
    public void deleteOption(UUID companyId, UUID userId, UUID catalogItemId, UUID optionId) {
        requireItem(companyId, catalogItemId);
        boolean deleted = optionRepository.delete(companyId, catalogItemId, optionId);
        if (!deleted) {
            throw new OptionNotFoundException();
        }
        auditLogger.log(companyId, userId, "floricultura_catalog_option_deleted", "floricultura_catalog_item_option",
            optionId, Map.of());
        catalogCache.invalidate(companyId);
    }

    private void requireItem(UUID companyId, UUID catalogItemId) {
        if (repository.findById(companyId, catalogItemId).isEmpty()) {
            throw new CatalogItemNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (FloriculturaCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
