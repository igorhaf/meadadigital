package com.meada.whatsapp.profiles.comida.menu;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.comida.ComidaCategory;
import com.meada.whatsapp.profiles.comida.ComidaMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do cardápio do comida (camada 8.4). Clone de
 * {@link com.meada.whatsapp.profiles.sushi.menu.SushiMenuService} + a gestão das OPÇÕES (ESCAPADA 2).
 * Valida categoria contra {@link ComidaCategory}, audita as mutações (audit_log do tenant) e invalida
 * o {@link ComidaMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class ComidaMenuService {

    private final ComidaMenuItemRepository repository;
    private final ComidaMenuOptionRepository optionRepository;
    private final AuditLogger auditLogger;
    private final ComidaMenuCache menuCache;

    public ComidaMenuService(ComidaMenuItemRepository repository,
                             ComidaMenuOptionRepository optionRepository,
                             AuditLogger auditLogger,
                             ComidaMenuCache menuCache) {
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
    public ComidaMenuItem create(UUID companyId, UUID userId, String name, String description,
                                 int priceCents, String category) {
        requireValidCategory(category);
        ComidaMenuItem created = repository.insert(companyId, name, description, priceCents, category);
        auditLogger.log(companyId, userId, "comida_menu_item_created", "comida_menu_item",
            created.id(), Map.of("name", created.name(), "category", created.category()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public ComidaMenuItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                 Integer priceCents, String category, Boolean available) {
        if (category != null && !category.isBlank()) {
            requireValidCategory(category);
        }
        ComidaMenuItem updated = repository.update(companyId, id, name, description, priceCents, category, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "comida_menu_item_updated", "comida_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public ComidaMenuItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        ComidaMenuItem item = repository.toggle(companyId, id, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "comida_menu_item_updated", "comida_menu_item", id,
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
            // FK restrict: existe comida_order_item apontando para este item.
            throw new MenuItemInUseException();
        }
        auditLogger.log(companyId, userId, "comida_menu_item_deleted", "comida_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<ComidaMenuItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<ComidaMenuItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    // ---- Opções (ESCAPADA 2) ------------------------------------------------

    public List<ComidaMenuOption> listOptions(UUID companyId, UUID menuItemId) {
        requireItem(companyId, menuItemId);
        return optionRepository.listByItem(companyId, menuItemId);
    }

    @Transactional
    public ComidaMenuOption addOption(UUID companyId, UUID userId, UUID menuItemId, String groupLabel,
                                      String optionLabel, int priceDeltaCents, int sortOrder) {
        requireItem(companyId, menuItemId);
        ComidaMenuOption created = optionRepository.insert(
            companyId, menuItemId, groupLabel, optionLabel, priceDeltaCents, sortOrder);
        auditLogger.log(companyId, userId, "comida_menu_option_created", "comida_menu_item_option",
            created.id(), Map.of("menu_item_id", menuItemId, "group_label", created.groupLabel()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public ComidaMenuOption updateOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId,
                                         String groupLabel, String optionLabel, Integer priceDeltaCents,
                                         Integer sortOrder, Boolean available) {
        requireItem(companyId, menuItemId);
        ComidaMenuOption updated = optionRepository.update(companyId, menuItemId, optionId,
                groupLabel, optionLabel, priceDeltaCents, sortOrder, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "comida_menu_option_updated", "comida_menu_item_option",
            optionId, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public ComidaMenuOption toggleOption(UUID companyId, UUID userId, UUID menuItemId, UUID optionId,
                                         boolean available) {
        requireItem(companyId, menuItemId);
        ComidaMenuOption option = optionRepository.toggle(companyId, menuItemId, optionId, available)
            .orElseThrow(OptionNotFoundException::new);
        auditLogger.log(companyId, userId, "comida_menu_option_updated", "comida_menu_item_option",
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
        auditLogger.log(companyId, userId, "comida_menu_option_deleted", "comida_menu_item_option",
            optionId, Map.of());
        menuCache.invalidate(companyId);
    }

    private void requireItem(UUID companyId, UUID menuItemId) {
        if (repository.findById(companyId, menuItemId).isEmpty()) {
            throw new MenuItemNotFoundException();
        }
    }

    private void requireValidCategory(String category) {
        if (ComidaCategory.fromId(category).isEmpty()) {
            throw new InvalidCategoryException();
        }
    }
}
