package com.meada.profiles.oficina.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.oficina.OficinaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Regras da config da oficina (camada 7.9). Lê com fallback; PUT upsert + audita + invalida cache. */
@Service
public class OficinaConfigService {

    private final OficinaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final OficinaContextCache contextCache;

    public OficinaConfigService(OficinaConfigRepository repository, AuditLogger auditLogger,
                                OficinaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidHoursException extends RuntimeException {}

    public OficinaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public OficinaConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                                boolean returnReminderEnabled, int returnReminderDays) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        OficinaConfig saved = repository.upsert(companyId, opensAt, closesAt, returnReminderEnabled,
            Math.max(30, Math.min(730, returnReminderDays)));
        auditLogger.log(companyId, userId, "os_config_updated", "os_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
