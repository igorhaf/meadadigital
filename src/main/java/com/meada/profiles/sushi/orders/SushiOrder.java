package com.meada.profiles.sushi.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido sushi (camada 7.1 / sushi funcional). DTO de saída com os itens + dados do contato + os
 * novos campos: desconto (cupom + fidelidade), fulfillment (entrega/retirada), agendamento (data +
 * período). {@code status} agora é o UUID de uma linha em {@code sushi_order_statuses} (o enum
 * SushiOrderStatus foi DELETADO); {@code statusName} é o nome resolvido via join (p/ exibição).
 */
public record SushiOrder(
    UUID id,
    UUID conversationId,
    UUID status,
    String statusName,
    int subtotalCents,
    int discountCents,
    int deliveryFeeCents,
    int totalCents,
    String couponCode,
    boolean loyaltyApplied,
    String fulfillment,
    String scheduledDate,
    String scheduledPeriod,
    String deliveryAddress,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<SushiOrderItem> items) {
}
