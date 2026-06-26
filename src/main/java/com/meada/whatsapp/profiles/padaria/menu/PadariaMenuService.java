package com.meada.whatsapp.profiles.padaria.menu;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.padaria.PadariaCategory;
import com.meada.whatsapp.profiles.padaria.PadariaMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do cardápio da padaria (camada 8.8 / perfil padaria). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogService} (catalog→menu) +
 * os campos da ESCAPADA 1 (made_to_order/lead_time_days) e allergens. Valida categoria contra
 * {@link PadariaCategory}, audita as mutações (audit_log do tenant) e invalida o
 * {@link PadariaMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class PadariaMenuService {

    private final PadariaMenuItemRepository repository;
    private final PadariaMenuOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final PadariaMenuCache menuCache;

    public PadariaMenuService(PadariaMenuItemRepository repository,
                              PadariaMenuOptionRepository optionRepository,
                              AuditLogger auditLogger,
                              PadariaMenuCache menuCache) {
        this.repository = repository;
        this.optionRepository = optionRepository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    /** Categoria inválida (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Item não encontrado / de outro tenant (→ 404). */
    public static class MenuItemNotFoundException extends RuntimeException {}

    /** Item referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class MenuItemInUseException extends RuntimeException {}

    /** Opção não encontrada / de outro item-tenant (→ 404). */
    public static class OptionNotFoundException extends RuntimeException {}

    // ---- Itens --------------------------------------------------------------

    @Transactional
    public PadariaMenuItem create(UUID companyId, UUID userId, String name, String description,
                                  int priceCents, String category, boolean madeToOrder,
                                  Integer leadTimeDays, String allergens) {
        requireValidCategory(category);
        PadariaMenuItem created = repository.insert(companyId, name, description, priceCents, category,
            madeToOrder, leadTimeDays, allergens);
        auditLogger.log(companyId, userId, "padaria_menu_item_created", "padaria_menu_item",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PadariaMenuItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                  Integer priceCents, String category, Boolean madeToOrder,
                                  Integer leadTimeDays, boolean clearLeadTime, String allergens,
                                  Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        PadariaMenuItem updated = repository.update(companyId, id, name, description, priceCents,
                category, madeToOrder, leadTimeDays, clearLeadTime, allergens, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "padaria_menu_item_updated", "padaria_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PadariaMenuItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        PadariaMenuItem item = repository.toggle(companyId, id, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "padaria_menu_item_updated", "padaria_menu_item", id,
            Map.of("available", available));
        menuCache.invalidate(companyId);
        return item;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            boolean deleted = repository.delete(companyId, id);
            if (!deleted) {
                throw new MenuItemNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            // FK restrict: existe padaria_order_item apontando para este item.
            throw new MenuItemInUseException();
        }
        auditLogger.log(companyId, userId, "padaria_menu_item_deleted", "padaria_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<PadariaMenuItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<PadariaMenuItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    public List<PadariaMenuOption> listOptions(UUID companyId, UUID menuItemId) {
        requireItem(companyId, menuItemId);
        return optionRepository.listByItem(companyId, menuItemId);
    }

    @Transactional
    public PadariaMenuOption addOption(UUID companyId, UUID userId, UUID menuItemId, String groupLabel,
                                       String optionLabel, int priceDeltaCents, int sortOrder) {
        requireItem(companyId, menuItemId);
        PadariaMenuOption created = optionRepository.insert(
            companyId, menuItemId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "padaria_menu_option_created", "padaria_menu_item_option",
            created.id(), Map.of("menu_item_id", menuItemId, "group_label", created.groupLabel()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PadariaMenuOption updateOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId,
                                          String groupLabel, String optionLabel, Integer priceDeltaCents,
                                          Integer sortOrder, Boolean available) {
        requireItem(companyId, menuItemId);
        PadariaMenuOption updated = optionRepository.update(companyId, menuItemId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "padaria_menu_option_updated", "padaria_menu_item_option",
            optionId, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PadariaMenuOption toggleOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId,
                                          boolean available) {
        requireItem(companyId, menuItemId);
        PadariaMenuOption option = optionRepository.toggle(companyId, menuItemId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "padaria_menu_option_updated", "padaria_menu_item_option",
            optionId, Map.of("available", available));
        menuCache.invalidate(companyId);
        return option;
    }

    @Transactional
    public void deleteOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId) {
        requireItem(companyId, menuItemId);
        boolean deleted = optionRepository.delete(companyId, menuItemId, optionId);
        if (!deleted) {
            throw new OptionNotFoundException();
        }
        auditLogger.log(companyId, userId, "padaria_menu_option_deleted", "padaria_menu_item_option",
            optionId, Map.of());
        menuCache.invalidate(companyId);
    }

    private void requireItem(UUID companyId, UUID menuItemId) {
        if (repository.findById(companyId, menuItemId).isEmpty()) {
            throw new MenuItemNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (PadariaCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
