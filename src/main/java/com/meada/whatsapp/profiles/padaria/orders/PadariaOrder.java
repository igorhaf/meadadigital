package com.meada.whatsapp.profiles.padaria.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pedido padaria (camada 8.8 / perfil padaria). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrder} adaptado às escapadas:
 * {@code fulfillment} (retirada/entrega), {@code pickupOrDeliveryDate} (data CONDICIONAL — obrigatória
 * só com item sob encomenda, ESCAPADA 1), {@code deliveryPeriod} (manha/tarde, nullable). DTO de saída
 * com os itens + dados do contato (comprador) para o card do Kanban. {@code rejectionReason} é o motivo
 * do gate de recusa.
 */
public record PadariaOrder(
    UUID id,
    UUID conversationId,
    String status,
    String fulfillment,
    int subtotalCents,
    int deliveryFeeCents,
    int totalCents,
    String deliveryAddress,
    LocalDate pickupOrDeliveryDate,
    String deliveryPeriod,
    String notes,
    String rejectionReason,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<PadariaOrderItem> items) {
}
