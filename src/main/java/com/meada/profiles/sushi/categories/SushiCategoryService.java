package com.meada.profiles.sushi.categories;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.sushi.SushiMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das categorias do cardápio sushi (camada 7.1 / sushi funcional). Valida o nome, audita as
 * mutações e invalida o {@link SushiMenuCache} a cada gravação (a IA vê a mudança na hora). Nome
 * duplicado (UNIQUE case-insensitive) → 409 duplicate_category; delete de categoria com itens →
 * 409 category_in_use (pré-check + catch do FK restrict).
 */
@Service
public class SushiCategoryService {

    private final SushiCategoryRepository repository;
    private final AuditLogger auditLogger;
    private final SushiMenuCache menuCache;

    public SushiCategoryService(SushiCategoryRepository repository, AuditLogger auditLogger,
                                SushiMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    /** Categoria não encontrada / de outro tenant (→ 404). */
    public static class CategoryNotFoundException extends RuntimeException {}

    /** Nome duplicado (UNIQUE company_id+lower(name)) → 409 duplicate_category. */
    public static class DuplicateCategoryException extends RuntimeException {}

    /** Nome inválido (vazio/só espaços) → 400 invalid_category_name. */
    public static class InvalidCategoryNameException extends RuntimeException {}

    /** Categoria com itens de cardápio (FK restrict) → 409 category_in_use. */
    public static class CategoryInUseException extends RuntimeException {}

    @Transactional
    public SushiCategoryEntity create(UUID companyId, UUID userId, String name, Integer sortOrder,
                                      Boolean active) {
        requireValidName(name);
        SushiCategoryEntity created;
        try {
            created = repository.insert(companyId, name, sortOrder == null ? 0 : sortOrder,
                active == null || active);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException();
        }
        auditLogger.log(companyId, userId, "sushi_category_created", "sushi_category",
            created.id(), Map.of("name", created.name()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public SushiCategoryEntity update(UUID companyId, UUID userId, UUID id, String name,
                                      Integer sortOrder, Boolean active) {
        if (name != null && !name.isBlank()) {
            requireValidName(name);
        } else if (name != null) {
            // name presente mas em branco → inválido.
            throw new InvalidCategoryNameException();
        }
        SushiCategoryEntity updated;
        try {
            updated = repository.update(companyId, id, name, sortOrder, active)
                .orElseThrow(CategoryNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException();
        }
        auditLogger.log(companyId, userId, "sushi_category_updated", "sushi_category", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public SushiCategoryEntity toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        SushiCategoryEntity cat = repository.toggle(companyId, id, active)
            .orElseThrow(CategoryNotFoundException::new);
        auditLogger.log(companyId, userId, "sushi_category_updated", "sushi_category", id,
            Map.of("active", active));
        menuCache.invalidate(companyId);
        return cat;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        // Pré-check (mensagem clara) — o FK restrict é a defesa final.
        if (repository.hasMenuItems(companyId, id)) {
            throw new CategoryInUseException();
        }
        try {
            if (!repository.delete(companyId, id)) {
                throw new CategoryNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new CategoryInUseException();
        }
        auditLogger.log(companyId, userId, "sushi_category_deleted", "sushi_category", id, Map.of());
        menuCache.invalidate(companyId);
    }

    public List<SushiCategoryEntity> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<SushiCategoryEntity> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    private static void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCategoryNameException();
        }
    }
}
