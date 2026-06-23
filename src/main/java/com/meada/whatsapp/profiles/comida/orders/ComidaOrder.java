package com.meada.whatsapp.profiles.comida.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido comida (camada 8.4). Clone de {@link com.meada.whatsapp.profiles.sushi.orders.SushiOrder}
 * + {@code rejectionReason} (ESCAPADA 1: motivo da recusa pelo restaurante). DTO de saída com os
 * itens + dados do contato (nome/telefone) para o card do Kanban.
 */
public record ComidaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int deliveryFeeCents,
    int totalCents,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<ComidaOrderItem> items) {
}
