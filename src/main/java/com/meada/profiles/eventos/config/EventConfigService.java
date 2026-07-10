package com.meada.profiles.eventos.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.eventos.EventosContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant eventos (camada 8.2). Lê com fallback; PUT upsert + audita + invalida
 * cache. SEM validação de horário — não há agenda (só nome do espaço + notas).
 */
@Service
public class EventConfigService {

    private final EventConfigRepository repository;
    private final AuditLogger auditLogger;
    private final EventosContextCache contextCache;

    public EventConfigService(EventConfigRepository repository, AuditLogger auditLogger,
                              EventosContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public EventConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public EventConfig update(UUID companyId, UUID userId, String businessName, String notes,
                              boolean autoCompleteEnabled, boolean postEventEnabled, String reviewLink,
                              boolean followUpEnabled, int followUpDays) {
        EventConfig saved = repository.upsert(companyId, businessName, notes, autoCompleteEnabled,
            postEventEnabled,
            reviewLink == null || reviewLink.isBlank() ? null : reviewLink.strip(),
            followUpEnabled, Math.min(60, Math.max(1, followUpDays)));
        auditLogger.log(companyId, userId, "event_config_updated", "event_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
