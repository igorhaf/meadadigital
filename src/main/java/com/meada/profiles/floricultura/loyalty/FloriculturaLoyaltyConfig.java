package com.meada.profiles.floricultura.loyalty;

import java.util.UUID;

/**
 * Config de fidelidade por contagem do tenant floricultura (camada 8.5, backlog #2 — clone do chassi
 * sushi). 1:1 com company. Quando {@code enabled}, o backend conta os pedidos ENTREGUES
 * (status='entregue') do contato; quando count &gt; 0 e count % thresholdOrders == 0, o próximo
 * pedido ganha o reward (percent no subtotal ou fixed em centavos). Ausência de linha → defaults
 * (enabled=false).
 */
public record FloriculturaLoyaltyConfig(
    UUID companyId,
    boolean enabled,
    int thresholdOrders,
    String rewardKind,
    int rewardValue) {

    /** Default quando o tenant não tem linha (fidelidade desligada). */
    public static FloriculturaLoyaltyConfig defaults(UUID companyId) {
        return new FloriculturaLoyaltyConfig(companyId, false, 10, "percent", 0);
    }
}
