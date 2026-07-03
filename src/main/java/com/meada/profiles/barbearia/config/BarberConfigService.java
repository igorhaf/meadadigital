package com.meada.profiles.barbearia.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.barbearia.BarberContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config da barbearia (camada 8.1). Lê com fallback aos defaults; o PUT faz upsert, audita
 * e invalida o {@link BarberContextCache}. Mudanças afetam agendamentos FUTUROS.
 * Espelho de SalonConfigService + slot_minutes e queue_enabled.
 */
@Service
public class BarberConfigService {

    private final BarberConfigRepository repository;
    private final AuditLogger auditLogger;
    private final BarberContextCache contextCache;

    public BarberConfigService(BarberConfigRepository repository, AuditLogger auditLogger,
                               BarberContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    /** slot_minutes <= 0 (→ 400 invalid_slot). */
    public static class InvalidSlotException extends RuntimeException {}

    public BarberConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public BarberConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                               int slotMinutes, boolean queueEnabled, boolean reminderEnabled,
                               boolean autoCompleteEnabled, boolean upsellEnabled) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        if (slotMinutes <= 0) {
            throw new InvalidSlotException();
        }
        BarberConfig saved = repository.upsert(companyId, opensAt, closesAt, slotMinutes, queueEnabled,
            reminderEnabled, autoCompleteEnabled, upsellEnabled);
        auditLogger.log(companyId, userId, "barber_config_updated", "barber_config", companyId,
            Map.of("slot_minutes", slotMinutes, "queue_enabled", queueEnabled));
        contextCache.invalidate(companyId);
        return saved;
    }
}
