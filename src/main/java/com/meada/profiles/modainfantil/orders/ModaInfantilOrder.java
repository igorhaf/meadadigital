package com.meada.profiles.modainfantil.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido moda_infantil (camada 8.22). Clone do
 * {@link com.meada.profiles.lingerie.orders.LingerieOrder} (gate de aceite:
 * {@code rejectionReason} na recusa; {@code fulfillment} entrega × retirada) + a coluna
 * {@code stockReturned} — ⭐ a adaptação 8.22 (marca se o estoque das variantes já foi devolvido no
 * cancelamento/recusa; garante idempotência do restock). DTO de saída com os itens + dados do contato
 * (nome/telefone) para o card do Kanban.
 */
public record ModaInfantilOrder(
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
    boolean stockReturned,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<ModaInfantilOrderItem> items) {
}
