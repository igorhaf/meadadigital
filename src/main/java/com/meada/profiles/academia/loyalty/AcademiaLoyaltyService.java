package com.meada.profiles.academia.loyalty;

import com.meada.common.audit.AuditLogger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Regras da fidelidade por assiduidade da academia (camada 7.7, feature #12). Lê/grava a config
 * (opt-in por tenant), consulta o saldo de um contato e credita pontos (a partir do check-in). O
 * crédito é SEMPRE positivo; o gate de "quando creditar" (a cada presença) é do fluxo de check-in —
 * aqui {@code addPoints} é um acumulador puro validado.
 *
 * <p>Trava academia: pontos são dado ADMINISTRATIVO de engajamento; a IA nunca prescreve treino/
 * dieta/avaliação nem promete resultado. Fidelidade só conta presença.
 */
@Service
public class AcademiaLoyaltyService {

    private final AcademiaLoyaltyRepository repository;
    private final AuditLogger auditLogger;

    public AcademiaLoyaltyService(AcademiaLoyaltyRepository repository, AuditLogger auditLogger) {
        this.repository = repository;
        this.auditLogger = auditLogger;
    }

    /** points_per_checkin < 1 ou reward_threshold < 1 (→ 400 invalid_config). */
    public static class InvalidConfigException extends RuntimeException {}

    /** Quantidade de pontos a creditar não é positiva (→ 400 invalid_points). */
    public static class InvalidPointsException extends RuntimeException {}

    /** Contato não encontrado / de outro tenant (→ 404 contact_not_found). */
    public static class ContactNotFoundException extends RuntimeException {}

    public AcademiaLoyaltyConfig getConfig(UUID companyId) {
        return repository.findConfig(companyId);
    }

    @Transactional
    public AcademiaLoyaltyConfig updateConfig(UUID companyId, UUID userId, boolean enabled,
                                              int pointsPerCheckin, Integer rewardThreshold, String rewardText) {
        if (pointsPerCheckin < 1) {
            throw new InvalidConfigException();
        }
        if (rewardThreshold != null && rewardThreshold < 1) {
            throw new InvalidConfigException();
        }
        String text = (rewardText == null || rewardText.isBlank()) ? null : rewardText.trim();
        AcademiaLoyaltyConfig saved = repository.upsertConfig(
            companyId, enabled, pointsPerCheckin, rewardThreshold, text);
        auditLogger.log(companyId, userId, "academia_loyalty_config_updated", "academia_loyalty_config",
            companyId, Map.of("enabled", Boolean.toString(enabled)));
        return saved;
    }

    public AcademiaLoyaltyBalance getBalance(UUID companyId, UUID contactId) {
        return repository.findBalance(companyId, contactId);
    }

    /** True se o saldo já atingiu o limiar de recompensa configurado (threshold definido e alcançado). */
    public boolean rewardReached(AcademiaLoyaltyConfig config, AcademiaLoyaltyBalance balance) {
        return config.rewardThreshold() != null && balance.points() >= config.rewardThreshold();
    }

    /** Credita {@code points} (>0) ao contato do tenant. Contato inexistente → 404; pontos <=0 → 400. */
    @Transactional
    public AcademiaLoyaltyBalance addPoints(UUID companyId, UUID userId, UUID contactId, int points) {
        if (points <= 0) {
            throw new InvalidPointsException();
        }
        if (!repository.contactExists(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        AcademiaLoyaltyBalance balance = repository.addPoints(companyId, contactId, points);
        auditLogger.log(companyId, userId, "academia_loyalty_points_added", "academia_loyalty",
            contactId, Map.of("points", Integer.toString(points), "balance", Integer.toString(balance.points())));
        return balance;
    }

    /**
     * Crédito AUTOMÁTICO de um check-in (feature #4 → #12): se a fidelidade do tenant está ligada,
     * credita {@code points_per_checkin} ao contato. NO-OP silencioso quando desligada — o check-in
     * nunca falha por causa da fidelidade. Chamado DENTRO da transação do registro de presença
     * (atômico: duplicata de check-in → 409 antes do crédito, sem ponto dobrado).
     */
    @Transactional
    public void creditForCheckin(UUID companyId, UUID userId, UUID contactId) {
        AcademiaLoyaltyConfig config = repository.findConfig(companyId);
        if (!config.enabled()) {
            return;
        }
        AcademiaLoyaltyBalance balance = repository.addPoints(companyId, contactId, config.pointsPerCheckin());
        auditLogger.log(companyId, userId, "academia_loyalty_checkin_credited", "academia_loyalty",
            contactId, Map.of("points", Integer.toString(config.pointsPerCheckin()),
                "balance", Integer.toString(balance.points())));
    }
}
