package com.meada.whatsapp.profiles.las.orders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Pedido las (camada 8.23). Clone do {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrder}
 * (gate de aceite: {@code rejectionReason} na recusa; {@code fulfillment} entrega × retirada) + a ⭐
 * ESCAPADA desta SM: {@code sameLotGuaranteed} (quando true, todos os itens da mesma cor têm de
 * referenciar o MESMO dye_lot — validado na criação, senão 422 mixed_dye_lots). DTO de saída com os
 * itens + dados do contato (nome/telefone) para o card do Kanban.
 */
public record LasOrder(
    UUID id,
    UUID conversationId,
    String status,
    String fulfillment,
    boolean sameLotGuaranteed,
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
    List<LasOrderItem> items) {
}
