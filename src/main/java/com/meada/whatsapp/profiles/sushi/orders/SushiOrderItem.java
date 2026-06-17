package com.meada.whatsapp.profiles.sushi.orders;

import java.util.UUID;

/**
 * Item de um pedido sushi (camada 7.1). itemName + unitPriceCents são SNAPSHOTS do momento do
 * pedido (menu_item_id pode ter mudado/sumido depois).
 */
public record SushiOrderItem(
    UUID id,
    UUID menuItemId,
    String itemName,
    int qtd,
    int unitPriceCents) {
}
