package com.meada.profiles.fotografia.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.fotografia.FotografiaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/** Regras da config do fotografia (camada 8.16). Lê com fallback; PUT upsert + audita + invalida cache. Espelho do DermatologiaConfigService. */
@Service
public class FotografiaConfigService {

    private static final int MIN_SLOT = 5;
    private static final int MAX_SLOT = 240;

    private final FotografiaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final FotografiaContextCache contextCache;

    public FotografiaConfigService(FotografiaConfigRepository repository, AuditLogger auditLogger, FotografiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidHoursException extends RuntimeException {}
    public static class InvalidSlotException extends RuntimeException {}

    public FotografiaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public FotografiaConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                                   int slotMinutes, boolean reminderEnabled, boolean autoCompleteEnabled,
                                   boolean autoDeliverEnabled, boolean postDeliveryUpsellEnabled,
                                   Integer cancellationPolicyHours) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        if (slotMinutes < MIN_SLOT || slotMinutes > MAX_SLOT) {
            throw new InvalidSlotException();
        }
        Integer policy = cancellationPolicyHours == null ? null
            : Math.min(720, Math.max(1, cancellationPolicyHours));
        FotografiaConfig saved = repository.upsert(companyId, opensAt, closesAt, slotMinutes,
            reminderEnabled, autoCompleteEnabled, autoDeliverEnabled, postDeliveryUpsellEnabled, policy);
        auditLogger.log(companyId, userId, "fotografia_config_updated", "fotografia_config", companyId, Map.of("slot_minutes", slotMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
