package com.meada.whatsapp.profiles.comida.orders;

import java.util.List;
import java.util.UUID;

/**
 * Item de um pedido comida (camada 8.4). Clone de
 * {@link com.meada.whatsapp.profiles.sushi.orders.SushiOrderItem} + a lista de {@code options}
 * escolhidas (ESCAPADA 2). itemName + unitPriceCents são SNAPSHOTS do momento do pedido
 * ({@code unitPriceCents} JÁ inclui a soma dos deltas das opções).
 */
public record ComidaOrderItem(
    UUID id,
    UUID menuItemId,
    String itemName,
    int qtd,
    int unitPriceCents,
    List<ComidaOrderItemOption> options) {
}
