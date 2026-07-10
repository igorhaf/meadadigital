package com.meada.profiles.suplementos;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da config do tenant suplementos (camada 8.24): taxa de entrega + pedido mínimo. Lê com
 * fallback (ZERO); PATCH upsert + audita + invalida o {@link SuplementosMenuCache} (taxa/mínimo
 * aparecem no prompt). Clone do padrão do LavanderiaConfigService.
 */
@Service
public class SuplementosConfigService {

    private final SuplementosConfigRepository repository;
    private final AuditLogger auditLogger;
    private final SuplementosMenuCache menuCache;

    public SuplementosConfigService(SuplementosConfigRepository repository, AuditLogger auditLogger,
                                    SuplementosMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    public SuplementosConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public SuplementosConfig update(UUID companyId, UUID userId, int deliveryFeeCents,
                                    int minOrderCents, Integer freeShippingThresholdCents) {
        SuplementosConfig saved = repository.upsert(companyId, Math.max(0, deliveryFeeCents),
            Math.max(0, minOrderCents),
            freeShippingThresholdCents == null ? null : Math.max(0, freeShippingThresholdCents));
        auditLogger.log(companyId, userId, "suplementos_config_updated", "sup_config", companyId, Map.of());
        menuCache.invalidate(companyId);
        return saved;
    }
}
