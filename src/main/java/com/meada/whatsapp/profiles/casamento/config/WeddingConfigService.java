package com.meada.whatsapp.profiles.casamento.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.casamento.CasamentoContextCache;
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
    public WeddingConfig update(UUID companyId, UUID userId, String businessName, String notes) {
        WeddingConfig saved = repository.upsert(companyId, businessName, notes);
        auditLogger.log(companyId, userId, "wedding_config_updated", "wedding_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
