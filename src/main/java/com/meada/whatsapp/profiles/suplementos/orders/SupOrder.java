package com.meada.whatsapp.profiles.suplementos.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido suplementos (camada 8.24). Análogo ao
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrder} (gate de aceite: {@code rejectionReason}
 * na recusa), mas SEM a coluna {@code fulfillment} — esta SM é SÓ ENTREGA ({@code deliveryAddress}
 * NOT NULL). DTO de saída com os itens + dados do contato (nome/telefone) para o card do Kanban.
 */
public record SupOrder(
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
    List<SupOrderItem> items) {
}
