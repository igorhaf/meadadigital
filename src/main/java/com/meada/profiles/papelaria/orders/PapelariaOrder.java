package com.meada.profiles.papelaria.orders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Pedido papelaria (camada 8.15 / perfil papelaria). Clone de
 * {@link com.meada.profiles.padaria.orders.PadariaOrder} (camada 8.8) + os campos da ESCAPADA
 * PROVA DE ARTE: {@code artApproved} (gate da transição arte_aprovacao→em_producao) e {@code artUrl}
 * (link/ref da arte subida pela equipe — sem upload). Mantém {@code fulfillment} (retirada/entrega),
 * {@code pickupOrDeliveryDate} (data CONDICIONAL — obrigatória só com item sob encomenda),
 * {@code deliveryPeriod} (manha/tarde, nullable). DTO de saída com os itens + dados do contato
 * (comprador) para o card do Kanban. {@code rejectionReason} é o motivo do gate de recusa.
 */
public record PapelariaOrder(
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
    boolean artApproved,
    String artUrl,
    Integer depositCents,
    boolean depositPaid,
    Instant depositPaidAt,
    String notes,
    String rejectionReason,
    Instant createdAt,
    Instant statusUpdatedAt,
    String contactName,
    String contactPhone,
    List<PapelariaOrderItem> items) {
}
