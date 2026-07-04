package com.meada.profiles.pet.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.pet.PetContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Regras da config do pet (camada 7.8). Lê com fallback; PUT upsert + audita + invalida cache. */
@Service
public class PetConfigService {

    private final PetConfigRepository repository;
    private final AuditLogger auditLogger;
    private final PetContextCache contextCache;

    public PetConfigService(PetConfigRepository repository, AuditLogger auditLogger, PetContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidHoursException extends RuntimeException {}

    public PetConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public PetConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt, int bufferMinutes,
                            boolean reminderEnabled) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        PetConfig saved = repository.upsert(companyId, opensAt, closesAt, bufferMinutes, reminderEnabled);
        auditLogger.log(companyId, userId, "pet_config_updated", "pet_config", companyId, Map.of("buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
