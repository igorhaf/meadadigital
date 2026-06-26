package com.meada.whatsapp.profiles.suplementos.orders;

import java.util.UUID;

/**
 * Item de um pedido suplementos (camada 8.24). Análogo ao
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderItem} (chassi de varejo), mas
 * carrega tanto {@code productId} quanto {@code variantId} (a migration 68 guarda os dois em
 * sup_order_items, ambos FK restrict) e o SNAPSHOT de produto+variante+preço do momento do pedido:
 * {@code productName} + {@code variantLabel} ("Chocolate 900g", sabor+peso congelados) +
 * {@code unitPriceCents}. Alterar/excluir produto/variante depois NÃO altera pedidos passados.
 */
public record SupOrderItem(
    UUID id,
    UUID productId,
    UUID variantId,
    String productName,
    String variantLabel,
    int qtd,
    int unitPriceCents) {
}
