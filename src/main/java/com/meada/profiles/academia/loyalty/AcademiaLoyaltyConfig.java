package com.meada.profiles.academia.loyalty;

import java.util.UUID;

/**
 * Config de fidelidade por assiduidade da academia (camada 7.7, feature #12) — espelha
 * academia_loyalty_config. 1:1 com company. Ausente → defaults (enabled=false, 1 ponto por check-in,
 * sem recompensa). {@code enabled} é opt-in explícito do tenant; {@code pointsPerCheckin} é quanto se
 * credita a cada presença; {@code rewardThreshold} (nullable) é quantos pontos liberam a recompensa;
 * {@code rewardText} descreve a recompensa (texto livre).
 */
public record AcademiaLoyaltyConfig(
    UUID companyId,
    boolean enabled,
    int pointsPerCheckin,
    Integer rewardThreshold,
    String rewardText) {

    public static AcademiaLoyaltyConfig defaultFor(UUID companyId) {
        return new AcademiaLoyaltyConfig(companyId, false, 1, null, null);
    }
}
