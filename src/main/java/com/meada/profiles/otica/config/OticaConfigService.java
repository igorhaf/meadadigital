package com.meada.profiles.otica.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.otica.OticaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config FUNDIDA do otica (camada 8.12). Lê com fallback aos defaults; o PUT faz upsert,
 * audita e invalida o {@link OticaContextCache} (janela/duração [FLUXO A] + mínimo/lead [FLUXO B]
 * entram no contexto da IA). Clone do {@code DentalClinicConfigService}.
 *
 * <p>Mudar a config afeta apenas exames FUTUROS — a duração é snapshotada em cada exame no momento
 * da criação (otica_exam_appointments.duration_minutes).
 */
@Service
public class OticaConfigService {

    private final OticaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final OticaContextCache contextCache;

    public OticaConfigService(OticaConfigRepository repository, AuditLogger auditLogger,
                              OticaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public OticaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public OticaConfig update(UUID companyId, UUID userId, LocalTime opensAt, LocalTime closesAt,
                              int examDurationMinutes, int minOrderCents, int leadTimeDaysDefault,
                              boolean examReminderEnabled, boolean pickupFollowupEnabled,
                              int pickupFollowupDays) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        OticaConfig saved = repository.upsert(
            companyId, opensAt, closesAt, examDurationMinutes, minOrderCents, leadTimeDaysDefault,
            examReminderEnabled, pickupFollowupEnabled,
            Math.max(1, Math.min(30, pickupFollowupDays)));
        auditLogger.log(companyId, userId, "otica_config_updated", "otica_config", companyId,
            Map.of("exam_duration_minutes", examDurationMinutes, "min_order_cents", minOrderCents,
                "lead_time_days_default", leadTimeDaysDefault));
        contextCache.invalidate(companyId);
        return saved;
    }
}
