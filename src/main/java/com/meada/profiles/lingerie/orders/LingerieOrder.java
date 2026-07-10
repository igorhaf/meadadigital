package com.meada.profiles.lingerie.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido lingerie (camada 8.21). Análogo ao {@link com.meada.profiles.adega.orders.AdegaOrder}
 * (gate de aceite: {@code rejectionReason} na recusa) — sem a coluna age_confirmed do adega, mas com
 * {@code fulfillment} (entrega × retirada). DTO de saída com os itens + dados do contato
 * (nome/telefone) para o card do Kanban.
 */
public record LingerieOrder(
    UUID id,
    UUID conversationId,
    String status,
    String fulfillment,
    int subtotalCents,
    int discountCents,
    int deliveryFeeCents,
    int totalCents,
    String couponCode,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<LingerieOrderItem> items) {
}
