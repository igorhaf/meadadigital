package com.meada.whatsapp.profiles.padaria.orders;

import java.util.UUID;

/**
 * Opção/personalização escolhida de um item de pedido padaria (camada 8.8 / perfil padaria,
 * ESCAPADA 2). DTO de saída. groupLabel/optionLabel/priceDeltaCents são SNAPSHOTS do momento do pedido
 * (a opção do cardápio pode ter mudado/sumido depois — {@code menuOptionId} vira null no
 * on-delete-set-null). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.FloriculturaOrderItemOption}.
 */
public record PadariaOrderItemOption(
    UUID id,
    UUID menuOptionId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents) {
}
