package com.meada.profiles.atelie.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.atelie.AtelieContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant atelie (camada 8.14). Lê com fallback; PUT upsert + audita + invalida
 * cache. SEM validação de horário — não há agenda (só nome do ateliê + notas). Espelho do
 * EventConfigService.
 */
@Service
public class AtelieConfigService {

    private final AtelieConfigRepository repository;
    private final AuditLogger auditLogger;
    private final AtelieContextCache contextCache;

    public AtelieConfigService(AtelieConfigRepository repository, AuditLogger auditLogger,
                               AtelieContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public AtelieConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public AtelieConfig update(UUID companyId, UUID userId, String businessName, String notes,
                               boolean fittingReminderEnabled, boolean postDeliveryEnabled,
                               String reviewLink, boolean reactivationEnabled, int reactivationDays) {
        AtelieConfig saved = repository.upsert(companyId, businessName, notes, fittingReminderEnabled,
            postDeliveryEnabled,
            reviewLink == null || reviewLink.isBlank() ? null : reviewLink.strip(),
            reactivationEnabled, Math.min(730, Math.max(7, reactivationDays)));
        auditLogger.log(companyId, userId, "atelie_config_updated", "atelie_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
