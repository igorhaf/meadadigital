package com.meada.profiles.sushi.loyalty;

import java.util.UUID;

/**
 * Config de fidelidade por contagem do tenant sushi (camada 7.1 / sushi funcional). 1:1 com company.
 * Quando {@code enabled}, o backend conta os pedidos ENTREGUES (terminais não-cancelados) do contato;
 * quando count &gt; 0 e count % thresholdOrders == 0, o próximo pedido ganha o reward (percent no
 * subtotal ou fixed em centavos). Ausência de linha → defaults (enabled=false).
 */
public record SushiLoyaltyConfig(
    UUID companyId,
    boolean enabled,
    int thresholdOrders,
    String rewardKind,
    int rewardValue) {

    /** Default quando o tenant não tem linha (fidelidade desligada). */
    public static SushiLoyaltyConfig defaults(UUID companyId) {
        return new SushiLoyaltyConfig(companyId, false, 10, "percent", 0);
    }
}
