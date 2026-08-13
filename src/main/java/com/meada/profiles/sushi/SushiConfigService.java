package com.meada.profiles.sushi;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant sushi (camada 7.1 / sushi funcional): taxa de entrega + pedido mínimo
 * + {@code schedulingEnabled}. Lê com fallback (ZERO); PATCH upsert + audita + invalida o
 * {@link SushiMenuCache} (taxa/mínimo aparecem no prompt). Math.max(0,...) só nos int; o boolean
 * vai direto.
 */
@Service
public class SushiConfigService {

    private final SushiRestaurantConfigRepository repository;
    private final AuditLogger auditLogger;
    private final SushiMenuCache menuCache;

    public SushiConfigService(SushiRestaurantConfigRepository repository, AuditLogger auditLogger,
                              SushiMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public SushiRestaurantConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public SushiRestaurantConfig update(UUID companyId, UUID userId, int deliveryFeeCents,
                                        int minOrderCents, boolean schedulingEnabled,
                                        boolean upsellEnabled, boolean reactivationEnabled,
                                        int reactivationDays, String reactivationCouponCode) {
        int days = Math.max(7, Math.min(180, reactivationDays));
        String couponCode = reactivationCouponCode == null || reactivationCouponCode.isBlank()
            ? null : reactivationCouponCode.trim();
        SushiRestaurantConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents), schedulingEnabled, upsellEnabled, reactivationEnabled,
            days, couponCode);
        auditLogger.log(companyId, userId, "sushi_config_updated", "sushi_restaurant_config",
            companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
