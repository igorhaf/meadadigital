package com.meada.profiles.floricultura.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pedido floricultura (camada 8.5). Clone de {@link com.meada.profiles.comida.orders.ComidaOrder}
 * + {@code rejectionReason} (gate de aceite) + a ESCAPADA da floricultura: entrega AGENDADA pra OUTRA
 * pessoa, com cartão — {@code deliveryDate} (dia), {@code deliveryPeriod} (manha/tarde),
 * {@code recipientName} (quem recebe), {@code cardMessage} (cartãozinho, nullable). DTO de saída com
 * os itens + dados do contato (comprador) para o card do Kanban.
 */
public record FloriculturaOrder(
    UUID id,
    UUID conversationId,
    String status,
    int subtotalCents,
    int discountCents,
    int deliveryFeeCents,
    int totalCents,
    String couponCode,
    boolean loyaltyApplied,
    boolean anonymous,
    String deliveryAddress,
    String notes,
    String rejectionReason,
    LocalDate deliveryDate,
    String deliveryPeriod,
    String recipientName,
    String cardMessage,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<FloriculturaOrderItem> items) {
}
