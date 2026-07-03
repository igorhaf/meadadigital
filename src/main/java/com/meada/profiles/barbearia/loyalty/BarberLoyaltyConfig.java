package com.meada.profiles.barbearia.loyalty;

import java.util.UUID;

/**
 * Config de fidelidade por contagem do tenant barbearia (onda 1, backlog #3): a cada
 * {@code thresholdCuts} agendamentos REALIZADOS do contato, o próximo sai GRÁTIS (desconto = preço
 * snapshot, {@code loyalty_applied=true}), materializado pelo backend na criação. 1:1 com company;
 * ausência de linha = desligada (threshold 10).
 */
public record BarberLoyaltyConfig(
    UUID companyId,
    boolean enabled,
    int thresholdCuts) {

    public static BarberLoyaltyConfig defaultFor(UUID companyId) {
        return new BarberLoyaltyConfig(companyId, false, 10);
    }
}
