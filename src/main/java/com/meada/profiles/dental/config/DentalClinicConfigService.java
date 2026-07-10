package com.meada.profiles.dental.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.dental.DentalContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do consultório (camada 7.4). Lê com fallback aos defaults; o PUT faz upsert,
 * audita e invalida o {@link DentalContextCache} (a duração/horário entra no contexto da IA).
 *
 * <p>Mudar a config afeta apenas consultas FUTURAS — a duração é snapshotada em cada consulta no
 * momento da criação (dental_appointments.duration_minutes).
 */
@Service
public class DentalClinicConfigService {

    private final DentalClinicConfigRepository repository;
    private final AuditLogger auditLogger;
    private final DentalContextCache contextCache;

    public DentalClinicConfigService(DentalClinicConfigRepository repository, AuditLogger auditLogger,
                                     DentalContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public DentalClinicConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public DentalClinicConfig update(UUID companyId, UUID userId, int durationMinutes,
                                     int bufferMinutes, LocalTime opensAt, LocalTime closesAt,
                                     boolean reminderEnabled, boolean autoCompleteEnabled,
                                     boolean recallEnabled, int recallMonths) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        DentalClinicConfig saved =
            repository.upsert(companyId, durationMinutes, bufferMinutes, opensAt, closesAt,
                reminderEnabled, autoCompleteEnabled, recallEnabled, Math.min(36, Math.max(1, recallMonths)));
        auditLogger.log(companyId, userId, "dental_config_updated", "dental_clinic_config",
            companyId, Map.of("duration_minutes", durationMinutes, "buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
