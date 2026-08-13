package com.meada.profiles.sushi.menu;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.sushi.SushiMenuCache;
import com.meada.profiles.sushi.categories.SushiCategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do cardápio do sushi (camada 7.1 / sushi funcional). Valida categoria contra a TABELA
 * {@code sushi_categories} (o enum SushiCategory foi DELETADO): categoria pode ser NULL (item sem
 * categoria), ou deve existir + ser do tenant + estar ativa. Audita as mutações e invalida o
 * {@link SushiMenuCache} a cada gravação — para a IA ver a mudança na hora.
 */
@Service
public class SushiMenuService {

    private final SushiMenuItemRepository repository;
    private final AuditLogger auditLogger;
    private final SushiMenuCache menuCache;
    private final SushiCategoryRepository categoryRepository;

    public SushiMenuService(SushiMenuItemRepository repository, AuditLogger auditLogger,
                            SushiMenuCache menuCache, SushiCategoryRepository categoryRepository) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
        this.categoryRepository = categoryRepository;
    }

    /** Categoria inválida (não existe/não é do tenant/inativa) (→ 400 invalid_category no controller). */
    public static class InvalidCategoryException extends RuntimeException {}

    /** Item não encontrado / de outro tenant (→ 404). */
    public static class MenuItemNotFoundException extends RuntimeException {}

    /** Item referenciado por pedido (FK restrict) — não pode hard-deletar (→ 409). */
    public static class MenuItemInUseException extends RuntimeException {}

    @Transactional
    public SushiMenuItem create(UUID companyId, UUID userId, String name, String description,
                                int priceCents, String category) {
        requireValidCategory(companyId, category);
        SushiMenuItem created = repository.insert(companyId, name, description, priceCents, category);
        auditLogger.log(companyId, userId, "sushi_menu_item_created", "sushi_menu_item",
            created.id(), Map.of("name", created.name()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public SushiMenuItem update(UUID companyId, UUID userId, UUID id, String name, String description,
                                Integer priceCents, String category, boolean categoryProvided,
                                Boolean available) {
        if (categoryProvided) {
            requireValidCategory(companyId, category);
        }
        SushiMenuItem updated = repository.update(companyId, id, name, description, priceCents,
                category, categoryProvided, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "sushi_menu_item_updated", "sushi_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public SushiMenuItem toggle(UUID companyId, UUID userId, UUID id, boolean available) {
        SushiMenuItem item = repository.toggle(companyId, id, available)
            .orElseThrow(MenuItemNotFoundException::new);
        auditLogger.log(companyId, userId, "sushi_menu_item_updated", "sushi_menu_item", id,
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
            // FK restrict: existe sushi_order_item apontando para este item.
            throw new MenuItemInUseException();
        }
        auditLogger.log(companyId, userId, "sushi_menu_item_deleted", "sushi_menu_item", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public java.util.List<SushiMenuItem> list(UUID companyId, String category, boolean onlyAvailable) {
        return repository.listByCompany(companyId, category, onlyAvailable);
    }

    public Optional<SushiMenuItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    /**
     * Categoria pode ser NULL (item sem categoria — permitido). Se informada, precisa ser um UUID
     * válido de uma categoria ATIVA do próprio tenant — senão InvalidCategoryException (400).
     */
    private void requireValidCategory(UUID companyId, String category) {
        if (category == null || category.isBlank()) {
            return;   // null/vazio = sem categoria (OK).
        }
        UUID categoryId;
        try {
            categoryId = UUID.fromString(category.trim());
        } catch (IllegalArgumentException e) {
            throw new InvalidCategoryException();
        }
        if (!categoryRepository.existsActive(companyId, categoryId)) {
            throw new InvalidCategoryException();
        }
    }
}
