package com.meada.whatsapp.profiles.academia.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.academia.AcademiaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config da academia (camada 7.7). Lê com fallback aos defaults; o PUT faz upsert, audita
 * e invalida o {@link AcademiaContextCache}.
 */
@Service
public class AcademiaConfigService {

    private final AcademiaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final AcademiaContextCache contextCache;

    public AcademiaConfigService(AcademiaConfigRepository repository, AuditLogger auditLogger,
                                 AcademiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public AcademiaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public AcademiaConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        AcademiaConfig saved = repository.upsert(companyId, opensAt, closesAt);
        auditLogger.log(companyId, userId, "academia_config_updated", "academia_config", companyId, Map.of());
        contextCache.invalidate(companyId);
        return saved;
    }
}
