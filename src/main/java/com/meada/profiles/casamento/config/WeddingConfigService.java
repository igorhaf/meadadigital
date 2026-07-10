package com.meada.profiles.casamento.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.casamento.CasamentoContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant casamento (camada 8.7). Lê com fallback; PUT upsert + audita + invalida
 * cache. SEM validação de horário — não há agenda (só nome da assessoria + notas). Espelho do
 * EventConfigService.
 */
@Service
public class WeddingConfigService {

    private final WeddingConfigRepository repository;
    private final AuditLogger auditLogger;
    private final CasamentoContextCache contextCache;

    public WeddingConfigService(WeddingConfigRepository repository, AuditLogger auditLogger,
                                CasamentoContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public WeddingConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public WeddingConfig update(UUID companyId, UUID userId, String businessName, String notes,
                                boolean checklistReminderEnabled, boolean paymentReminderEnabled,
                                boolean autoCompleteEnabled, boolean anniversaryEnabled,
                                boolean postEventEnabled, String reviewLink,
                                boolean followUpEnabled, int followUpDays) {
        WeddingConfig saved = repository.upsert(companyId, businessName, notes, checklistReminderEnabled,
            paymentReminderEnabled, autoCompleteEnabled, anniversaryEnabled, postEventEnabled,
            reviewLink == null || reviewLink.isBlank() ? null : reviewLink.strip(),
            followUpEnabled, Math.min(60, Math.max(1, followUpDays)));
        auditLogger.log(companyId, userId, "wedding_config_updated", "wedding_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
