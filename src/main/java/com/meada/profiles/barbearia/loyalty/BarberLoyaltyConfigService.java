package com.meada.profiles.barbearia.loyalty;

import com.meada.common.audit.AuditLogger;
import com.meada.profiles.barbearia.BarberContextCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da fidelidade da barbearia (onda 1, backlog #3). GET com fallback (desligada); PUT upsert +
 * audita + invalida o contexto da IA (que informa o saldo ao cliente). threshold &lt; 1 →
 * invalid_loyalty.
 */
@Service
public class BarberLoyaltyConfigService {

    private final BarberLoyaltyConfigRepository repository;
    private final AuditLogger auditLogger;
    private final BarberContextCache contextCache;

    public BarberLoyaltyConfigService(BarberLoyaltyConfigRepository repository, AuditLogger auditLogger,
                                      BarberContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class InvalidLoyaltyException extends RuntimeException {}

    public BarberLoyaltyConfig get(UUID companyId) {
        return repository.findByCompany(companyId);
    }

    @Transactional
    public BarberLoyaltyConfig update(UUID companyId, UUID userId, boolean enabled, int thresholdCuts) {
        if (thresholdCuts < 1) {
            throw new InvalidLoyaltyException();
        }
        BarberLoyaltyConfig saved = repository.upsert(companyId, enabled, thresholdCuts);
        auditLogger.log(companyId, userId, "barber_loyalty_updated", "barber_loyalty_config", companyId,
            Map.of("enabled", enabled, "threshold_cuts", thresholdCuts));
        contextCache.invalidate(companyId);
        return saved;
    }
}
