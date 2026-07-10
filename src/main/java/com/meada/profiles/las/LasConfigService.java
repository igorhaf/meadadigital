package com.meada.profiles.las;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant las (camada 8.23): taxa de entrega + pedido mínimo. Lê com fallback
 * (ZERO); PATCH upsert + audita + invalida o {@link LasMenuCache} (taxa/mínimo aparecem no prompt).
 */
@Service
public class LasConfigService {

    private final LasConfigRepository repository;
    private final AuditLogger auditLogger;
    private final LasMenuCache menuCache;

    public LasConfigService(LasConfigRepository repository, AuditLogger auditLogger,
                            LasMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public LasConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public LasConfig update(UUID companyId, UUID userId, int deliveryFeeCents, int minOrderCents,
                            boolean reactivationEnabled, int reactivationDays,
                            String reactivationCouponCode) {
        LasConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), reactivationEnabled,
            Math.min(365, Math.max(7, reactivationDays)),
            reactivationCouponCode == null || reactivationCouponCode.isBlank()
                ? null : reactivationCouponCode.strip());
        auditLogger.log(companyId, userId, "las_config_updated", "las_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
