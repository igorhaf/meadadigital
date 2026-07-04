package com.meada.profiles.lavanderia.config;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.lavanderia.LavanderiaCatalogCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant lavanderia (camada 8.10). Lê com fallback; PUT upsert + audita + invalida
 * o {@link LavanderiaCatalogCache} (taxa/mínimo/turnaround default aparecem no prompt). Clone do padrão
 * do AtelieConfigService.
 */
@Service
public class LavanderiaConfigService {

    private final LavanderiaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final LavanderiaCatalogCache catalogCache;

    public LavanderiaConfigService(LavanderiaConfigRepository repository, AuditLogger auditLogger,
                                   LavanderiaCatalogCache catalogCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.catalogCache = catalogCache;
    }

    public LavanderiaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public LavanderiaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents,
                                   int turnaroundDaysDefault, boolean expressEnabled,
                                   int expressSurchargePct, int expressTurnaroundDays,
                                   boolean collectReminderEnabled, boolean readyReminderEnabled,
                                   int readyReminderDays, boolean reactivationEnabled,
                                   int reactivationDays, String reactivationCouponCode) {
        LavanderiaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), Math.max(0, turnaroundDaysDefault), expressEnabled,
            Math.min(300, Math.max(0, expressSurchargePct)),
            Math.min(30, Math.max(0, expressTurnaroundDays)),
            collectReminderEnabled, readyReminderEnabled,
            Math.min(30, Math.max(1, readyReminderDays)), reactivationEnabled,
            Math.min(365, Math.max(7, reactivationDays)),
            reactivationCouponCode == null || reactivationCouponCode.isBlank()
                ? null : reactivationCouponCode.strip());
        auditLogger.log(companyId, userId, "lavanderia_config_updated", "lavanderia_config", companyId, Map.of());
        catalogCache.invalidate(companyId);
        return saved;
    }
}
