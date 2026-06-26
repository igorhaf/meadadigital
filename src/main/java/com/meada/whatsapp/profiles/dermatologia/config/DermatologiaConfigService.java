package com.meada.whatsapp.profiles.dermatologia.config;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Regras da config do dermatologia (camada 8.11). Lê com fallback; PUT upsert + audita + invalida cache. */
@Service
public class DermatologiaConfigService {

    private final DermatologiaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final DermatologiaContextCache contextCache;

    public DermatologiaConfigService(DermatologiaConfigRepository repository, AuditLogger auditLogger, DermatologiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidHoursException extends RuntimeException {}

    public DermatologiaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public DermatologiaConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt, int bufferMinutes) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        DermatologiaConfig saved = repository.upsert(companyId, opensAt, closesAt, bufferMinutes);
        auditLogger.log(companyId, userId, "dermatologia_config_updated", "dermatologia_config", companyId, Map.of("buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
