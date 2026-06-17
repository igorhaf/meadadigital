package com.meada.whatsapp.profiles.pousada.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.pousada.PousadaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config da pousada (camada 7.6). Lê com fallback aos defaults; o PUT faz upsert, audita e
 * invalida o {@link PousadaContextCache}. Não há validação de ordem entre check_in/check_out (são
 * horários de DIAS diferentes — check-out 11:00 < check-in 14:00 é normal).
 */
@Service
public class PousadaConfigService {

    private final PousadaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final PousadaContextCache contextCache;

    public PousadaConfigService(PousadaConfigRepository repository, AuditLogger auditLogger,
                                PousadaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public PousadaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public PousadaConfig update(UUID companyId, UUID userId, LocalTime checkInTime,
                               LocalTime checkOutTime, String cancellationPolicy) {
        PousadaConfig saved = repository.upsert(companyId, checkInTime, checkOutTime, cancellationPolicy);
        auditLogger.log(companyId, userId, "pousada_config_updated", "pousada_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
