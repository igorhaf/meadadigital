package com.meada.whatsapp.profiles.padaria.orders;

import java.util.List;
import java.util.UUID;

/**
 * Item de um pedido padaria (camada 8.8 / perfil padaria). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderItem} + a ESCAPADA 1
 * ({@code madeToOrder} snapshot) e a ESCAPADA 2 ({@code cakeMessage} snapshot da placa, nullable).
 * itemName + unitPriceCents são SNAPSHOTS do momento do pedido ({@code unitPriceCents} JÁ inclui a
 * soma dos deltas das opções).
 */
public record PadariaOrderItem(
    UUID id,
    UUID menuItemId,
    String itemName,
    int qtd,
    int unitPriceCents,
    boolean madeToOrder,
    String cakeMessage,
    List<PadariaOrderItemOption> options) {
}
