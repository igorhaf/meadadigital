package com.meada.profiles.comida;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant comida (camada 8.4): taxa de entrega + pedido mínimo. Lê com
 * fallback (ZERO); PATCH upsert + audita + invalida o {@link ComidaMenuCache} (taxa/mínimo
 * aparecem no prompt). Clone do padrão do SuplementosConfigService.
 */
@Service
public class ComidaConfigService {

    private final ComidaConfigRepository repository;
    private final AuditLogger auditLogger;
    private final ComidaMenuCache menuCache;

    public ComidaConfigService(ComidaConfigRepository repository, AuditLogger auditLogger,
                               ComidaMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public ComidaConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public ComidaConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents,
                               java.time.LocalTime opensAt, java.time.LocalTime closesAt,
                               Integer autoDeliverHours, boolean reactivationEnabled,
                               int reactivationDays, String reactivationCouponCode) {
        if (opensAt != null && closesAt != null && !opensAt.isBefore(closesAt)) {
            throw new IllegalArgumentException("invalid_hours");
        }
        ComidaConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), opensAt, closesAt,
            autoDeliverHours == null ? null : Math.min(24, Math.max(1, autoDeliverHours)),
            reactivationEnabled, Math.min(365, Math.max(7, reactivationDays)),
            reactivationCouponCode == null || reactivationCouponCode.isBlank()
                ? null : reactivationCouponCode.strip());
        auditLogger.log(companyId, userId, "comida_config_updated", "comida_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
