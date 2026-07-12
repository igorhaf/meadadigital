package com.meada.profiles.comida.loyalty;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.comida.ComidaMenuCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da fidelidade comida (onda 1 do comida, backlog #2 — clone do chassi sushi). get (com fallback p/
 * defaults) + update (upsert + invalidação do ComidaMenuCache — o contexto da IA embute o bloco
 * FIDELIDADE). Valida reward_kind ('percent'/'fixed') e reward_value (percent 0..100, fixed &gt;= 0)
 * e threshold_orders (&gt;= 1). A APLICAÇÃO da fidelidade acontece na criação do pedido, não aqui.
 */
@Service
public class ComidaLoyaltyConfigService {

    private final ComidaLoyaltyConfigRepository repository;
    private final AuditLogger auditLogger;
    private final ComidaMenuCache menuCache;

    public ComidaLoyaltyConfigService(ComidaLoyaltyConfigRepository repository, AuditLogger auditLogger,
                                      ComidaMenuCache menuCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.menuCache = menuCache;
    }

    /** Config inválida (reward/threshold) → 400 invalid_loyalty_config. */
    public static class InvalidLoyaltyConfigException extends RuntimeException {}

    public ComidaLoyaltyConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public ComidaLoyaltyConfig update(UUID companyId, UUID userId, boolean enabled, int thresholdOrders,
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
        ComidaLoyaltyConfig saved = repository.upsert(companyId, enabled, thresholdOrders, rewardKind, rewardValue);
        auditLogger.log(companyId, userId, "comida_loyalty_config_updated", "comida_loyalty_config",
            companyId, Map.of("enabled", enabled));
        // O segmento cacheado do prompt embute o programa de fidelidade — invalida para a IA
        // não anunciar prêmio/threshold velho até o TTL (mesmo padrão do BarberLoyaltyConfigService).
        menuCache.invalidate(companyId);
        return saved;
    }
}
