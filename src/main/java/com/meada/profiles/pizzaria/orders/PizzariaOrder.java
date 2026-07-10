package com.meada.profiles.pizzaria.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido pizzaria (camada 8.4). Clone de {@link com.meada.profiles.sushi.orders.SushiOrder}
 * + {@code rejectionReason} (ESCAPADA 1: motivo da recusa pelo restaurante). DTO de saída com os
 * itens + dados do contato (nome/telefone) para o card do Kanban.
 */
public record PizzariaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int discountCents,
    int deliveryFeeCents,
    int totalCents,
    String couponCode,
    boolean loyaltyApplied,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<PizzariaOrderItem> items) {
}
