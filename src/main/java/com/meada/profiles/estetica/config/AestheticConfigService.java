package com.meada.profiles.estetica.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.estetica.EsteticaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Regras da config do tenant estetica (camada 8.3). Lê com fallback; PUT upsert + audita + invalida cache. */
@Service
public class AestheticConfigService {

    private final AestheticConfigRepository repository;
    private final AuditLogger auditLogger;
    private final EsteticaContextCache contextCache;

    public AestheticConfigService(AestheticConfigRepository repository, AuditLogger auditLogger,
                                  EsteticaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidHoursException extends RuntimeException {}

    public AestheticConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public AestheticConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                                  int slotMinutes, boolean reminderEnabled, boolean autoCompleteEnabled,
                                  boolean autoExpireEnabled, Integer packageValidityDays,
                                  boolean renewalEnabled, int renewalDays, int expiryWarningDays) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        Integer validity = packageValidityDays == null ? null
            : Math.min(1095, Math.max(7, packageValidityDays));
        AestheticConfig saved = repository.upsert(companyId, opensAt, closesAt, slotMinutes,
            reminderEnabled, autoCompleteEnabled, autoExpireEnabled, validity, renewalEnabled,
            Math.min(365, Math.max(7, renewalDays)), Math.min(60, Math.max(1, expiryWarningDays)));
        auditLogger.log(companyId, userId, "aesthetic_config_updated", "aesthetic_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
