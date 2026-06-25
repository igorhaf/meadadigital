package com.meada.whatsapp.profiles.escola.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.escola.EscolaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config da escola (camada 8.19). Lê com fallback aos defaults; o PUT faz upsert, audita
 * e invalida o {@link EscolaContextCache}.
 */
@Service
public class EscolaConfigService {

    private final EscolaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final EscolaContextCache contextCache;

    public EscolaConfigService(EscolaConfigRepository repository, AuditLogger auditLogger,
                               EscolaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public EscolaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public EscolaConfig update(UUID companyId, UUID userId, String businessName, LocalTime opensAt,
                               LocalTime closesAt, String notes) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        EscolaConfig saved = repository.upsert(companyId, businessName, opensAt, closesAt, notes);
        auditLogger.log(companyId, userId, "escola_config_updated", "escola_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
