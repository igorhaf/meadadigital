package com.meada.profiles.concessionaria.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.concessionaria.ConcessionariaContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

/**
 * Regras da config da concessionaria (camada 8.17). Lê com fallback aos defaults; o PUT faz upsert,
 * audita e invalida o {@link ConcessionariaContextCache} (a duração/horário/nome entra no contexto da IA).
 *
 * <p>Mudar a config afeta apenas test-drives FUTUROS — a duração é snapshotada em cada test-drive no
 * momento da criação (concessionaria_test_drives.duration_minutes).
 */
@Service
public class ConcessionariaConfigService {

    private final ConcessionariaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final ConcessionariaContextCache contextCache;

    public ConcessionariaConfigService(ConcessionariaConfigRepository repository, AuditLogger auditLogger,
                                       ConcessionariaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** opens_at >= closes_at (janela inválida) (→ 400 invalid_hours). */
    public static class InvalidHoursException extends RuntimeException {}

    public ConcessionariaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public ConcessionariaConfig update(UUID companyId, UUID userId, String businessName,
                                       int durationMinutes, int bufferMinutes, LocalTime opensAt,
                                       LocalTime closesAt, String notes, boolean followupEnabled,
                                       int followupDays, boolean testdriveReminderEnabled,
                                       boolean autoCompleteEnabled, boolean postSaleEnabled,
                                       String reviewLink, boolean serviceReminderEnabled,
                                       int serviceReminderMonths) {
        if (!opensAt.isBefore(closesAt)) {
            throw new InvalidHoursException();
        }
        ConcessionariaConfig saved =
            repository.upsert(companyId, businessName, durationMinutes, bufferMinutes, opensAt, closesAt,
                notes, followupEnabled, Math.max(1, followupDays), testdriveReminderEnabled,
                autoCompleteEnabled, postSaleEnabled,
                reviewLink == null || reviewLink.isBlank() ? null : reviewLink.strip(),
                serviceReminderEnabled, Math.min(36, Math.max(1, serviceReminderMonths)));
        auditLogger.log(companyId, userId, "concessionaria_config_updated", "concessionaria_config",
            companyId, Map.of("duration_minutes", durationMinutes, "buffer_minutes", bufferMinutes));
        contextCache.invalidate(companyId);
        return saved;
    }
}
