package com.meada.profiles.oficina.catalog;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.oficina.OficinaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras do catálogo de materiais/técnicas do oficina (onda 2, backlog #15). CRUD + auditoria +
 * invalidação do contexto da IA (o upsell do backlog #10 injeta os NOMES ativos do catálogo).
 * Preço negativo/nome vazio → invalid_item. Delete é livre (o item de orçamento é snapshot texto).
 */
@Service
public class OficinaCatalogService {

    private final OficinaCatalogRepository repository;
    private final AuditLogger auditLogger;
    private final OficinaContextCache contextCache;

    public OficinaCatalogService(OficinaCatalogRepository repository, AuditLogger auditLogger,
                                OficinaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class CatalogItemNotFoundException extends RuntimeException {}
    public static class InvalidCatalogItemException extends RuntimeException {}

    public List<OficinaCatalogItem> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<OficinaCatalogItem> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    @Transactional
    public OficinaCatalogItem create(UUID companyId, UUID userId, String name, String category,
                                    Integer unitPriceCents, Boolean active, String notes) {
        if (name == null || name.isBlank() || name.trim().length() > 200
            || unitPriceCents == null || unitPriceCents < 0) {
            throw new InvalidCatalogItemException();
        }
        OficinaCatalogItem created = repository.insert(companyId, name, category, unitPriceCents,
            active == null || active, notes);
        auditLogger.log(companyId, userId, "oficina_catalog_item_created", "oficina_catalog_item",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public OficinaCatalogItem update(UUID companyId, UUID userId, UUID id, String name, String category,
                                    boolean categoryProvided, Integer unitPriceCents, Boolean active,
                                    String notes, boolean notesProvided) {
        if ((name != null && (name.isBlank() || name.trim().length() > 200))
            || (unitPriceCents != null && unitPriceCents < 0)) {
            throw new InvalidCatalogItemException();
        }
        OficinaCatalogItem updated = repository.update(companyId, id, name, category, categoryProvided,
            unitPriceCents, active, notes, notesProvided).orElseThrow(CatalogItemNotFoundException::new);
        auditLogger.log(companyId, userId, "oficina_catalog_item_updated", "oficina_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new CatalogItemNotFoundException();
        }
        auditLogger.log(companyId, userId, "oficina_catalog_item_deleted", "oficina_catalog_item", id, Map.of());
        contextCache.invalidate(companyId);
    }
}
