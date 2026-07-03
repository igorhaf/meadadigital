package com.meada.profiles.comida.zones;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.comida.ComidaMenuCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Regras das zonas de entrega (onda 1 do comida, backlog #8). CRUD + auditoria + invalidação do
 * ComidaMenuCache (o contexto da IA lista as zonas com id EXATO). Nome duplicado (UNIQUE
 * case-insensitive) → 409 duplicate_zone; nome vazio/taxa negativa → 400 invalid_zone.
 */
@Service
public class ComidaDeliveryZoneService {

    private final ComidaDeliveryZoneRepository repository;
    private final AuditLogger auditLogger;
    private final ComidaMenuCache menuCache;

    public ComidaDeliveryZoneService(ComidaDeliveryZoneRepository repository, AuditLogger auditLogger,
                                     ComidaMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public static class ZoneNotFoundException extends RuntimeException {}
    public static class DuplicateZoneException extends RuntimeException {}
    public static class InvalidZoneException extends RuntimeException {}

    public List<ComidaDeliveryZone> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    @Transactional
    public ComidaDeliveryZone create(UUID companyId, UUID userId, String name, Integer feeCents, Boolean active) {
        if (name == null || name.isBlank() || name.trim().length() > 120 || feeCents == null || feeCents < 0) {
            throw new InvalidZoneException();
        }
        ComidaDeliveryZone created;
        try {
            created = repository.insert(companyId, name, feeCents, active == null || active);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateZoneException();
        }
        auditLogger.log(companyId, userId, "comida_zone_created", "comida_delivery_zone",
            created.id(), Map.of("name", created.name()));
        menuCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public ComidaDeliveryZone update(UUID companyId, UUID userId, UUID id, String name, Integer feeCents,
                                     Boolean active) {
        if ((name != null && (name.isBlank() || name.trim().length() > 120))
            || (feeCents != null && feeCents < 0)) {
            throw new InvalidZoneException();
        }
        ComidaDeliveryZone updated;
        try {
            updated = repository.update(companyId, id, name, feeCents, active)
                .orElseThrow(ZoneNotFoundException::new);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateZoneException();
        }
        auditLogger.log(companyId, userId, "comida_zone_updated", "comida_delivery_zone", id, Map.of());
        menuCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        if (!repository.delete(companyId, id)) {
            throw new ZoneNotFoundException();
        }
        auditLogger.log(companyId, userId, "comida_zone_deleted", "comida_delivery_zone", id, Map.of());
        menuCache.invalidate(companyId);
    }
}
