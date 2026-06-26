package com.meada.whatsapp.profiles.viagens.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.viagens.ViagensContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant viagens (camada 8.18 / perfil viagens). Lê com fallback; PUT upsert +
 * audita + invalida cache. SEM validação de horário — não há agenda (só nome da agência + notas).
 * Espelho EXATO do EventConfigService (chassi eventos 8.2).
 */
@Service
public class TravelConfigService {

    private final TravelConfigRepository repository;
    private final AuditLogger auditLogger;
    private final ViagensContextCache contextCache;

    public TravelConfigService(TravelConfigRepository repository, AuditLogger auditLogger,
                               ViagensContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public TravelConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public TravelConfig update(UUID companyId, UUID userId, String businessName, String notes) {
        TravelConfig saved = repository.upsert(companyId, businessName, notes);
        auditLogger.log(companyId, userId, "travel_config_updated", "travel_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
