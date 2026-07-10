package com.meada.profiles.salon.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.salon.SalonContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do salão (camada 7.5). Lê com fallback aos defaults; o PUT faz upsert, audita e
 * invalida o {@link SalonContextCache}. Mudanças afetam agendamentos FUTUROS.
 */
@Service
public class SalonConfigService {

    private final SalonConfigRepository repository;
    private final AuditLogger auditLogger;
    private final SalonContextCache contextCache;

    public SalonConfigService(SalonConfigRepository repository, AuditLogger auditLogger,
                              SalonContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public SalonConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public SalonConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                              int bufferMinutes, boolean reminderEnabled, boolean autoCompleteEnabled) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        SalonConfig saved = repository.upsert(companyId, opensAt, closesAt, bufferMinutes,
            reminderEnabled, autoCompleteEnabled);
        auditLogger.log(companyId, userId, "salon_config_updated", "salon_config", companyId,
            Map.of("buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
