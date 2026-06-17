package com.meada.whatsapp.profiles.sushi.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido sushi (camada 7.1). DTO de saída com os itens + dados do contato (nome/telefone) para
 * o card do Kanban.
 */
public record SushiOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int deliveryFeeCents,
    int totalCents,
    String deliveryAddress,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<SushiOrderItem> items) {
}
