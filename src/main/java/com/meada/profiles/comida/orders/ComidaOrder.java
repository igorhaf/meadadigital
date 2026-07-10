package com.meada.profiles.comida.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido comida (camada 8.4). Clone de {@link com.meada.profiles.sushi.orders.SushiOrder}
 * + {@code rejectionReason} (ESCAPADA 1: motivo da recusa pelo restaurante). DTO de saída com os
 * itens + dados do contato (nome/telefone) para o card do Kanban. ONDA 1 do backlog: desconto
 * materializado (cupom #1 + fidelidade #2, clampado ao subtotal; total = subtotal − discount +
 * delivery_fee) e taxa por zona (#8 — {@code zoneNameSnapshot}; null = taxa flat da config).
 */
public record ComidaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int discountCents,
    int deliveryFeeCents,
    int totalCents,
    String couponCodeSnapshot,
    boolean loyaltyApplied,
    String zoneNameSnapshot,
    String fulfillment,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<ComidaOrderItem> items) {
}
