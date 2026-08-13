package com.meada.profiles.sushi.loyalty;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da fidelidade sushi (camada 7.1 / sushi funcional). get (com fallback p/ defaults) + update
 * (upsert). Valida reward_kind ('percent'/'fixed') e reward_value (percent 0..100, fixed &gt;= 0) e
 * threshold_orders (&gt;= 1). A APLICAÇÃO da fidelidade acontece na criação do pedido, não aqui.
 */
@Service
public class SushiLoyaltyConfigService {

    private final SushiLoyaltyConfigRepository repository;
    private final AuditLogger auditLogger;

    public SushiLoyaltyConfigService(SushiLoyaltyConfigRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /** Config inválida (reward/threshold) → 400 invalid_loyalty_config. */
    public static class InvalidLoyaltyConfigException extends RuntimeException {}

    public SushiLoyaltyConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public SushiLoyaltyConfig update(UUID companyId, UUID userId, boolean enabled, int thresholdOrders,
                                     String rewardKind, int rewardValue) {
        if (thresholdOrders < 1) {
            throw new InvalidLoyaltyConfigException();
        }
        if (!"percent".equals(rewardKind) && !"fixed".equals(rewardKind)) {
            throw new InvalidLoyaltyConfigException();
        }
        if ("percent".equals(rewardKind)) {
            if (rewardValue < 0 || rewardValue > 100) {
                throw new InvalidLoyaltyConfigException();
            }
        } else if (rewardValue < 0) {
            throw new InvalidLoyaltyConfigException();
        }
        SushiLoyaltyConfig saved = repository.upsert(companyId, enabled, thresholdOrders, rewardKind, rewardValue);
        auditLogger.log(companyId, userId, "sushi_loyalty_config_updated", "sushi_loyalty_config",
            companyId, Map.of("enabled", enabled));
        return saved;
    }
}
