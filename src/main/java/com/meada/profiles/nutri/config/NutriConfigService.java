package com.meada.profiles.nutri.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.nutri.NutriContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Regras da config do nutri (camada 8.0). Lê com fallback; PUT upsert + audita + invalida cache. */
@Service
public class NutriConfigService {

    private final NutriConfigRepository repository;
    private final AuditLogger auditLogger;
    private final NutriContextCache contextCache;

    public NutriConfigService(NutriConfigRepository repository, AuditLogger auditLogger, NutriContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidHoursException extends RuntimeException {}

    public NutriConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public NutriConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                              int bufferMinutes, boolean reminderEnabled, boolean autoCompleteEnabled,
                              boolean reengagementEnabled, int reengagementDays) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        NutriConfig saved = repository.upsert(companyId, opensAt, closesAt, bufferMinutes,
            reminderEnabled, autoCompleteEnabled, reengagementEnabled,
            Math.max(7, Math.min(365, reengagementDays)));
        auditLogger.log(companyId, userId, "nutri_config_updated", "nutri_config", companyId, Map.of("buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
