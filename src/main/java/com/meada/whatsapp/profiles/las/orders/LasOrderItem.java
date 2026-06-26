package com.meada.whatsapp.profiles.las.orders;

import java.util.UUID;

/**
 * Item de um pedido las (camada 8.23). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.orders.LingerieOrderItem} com o eixo de variante TROCADO
 * (size→dyeLot): referencia a VARIANTE e carrega o SNAPSHOT de produto+variante+preço do momento do
 * pedido: {@code productName} + {@code color} + {@code dyeLot} + {@code unitPriceCents}. Alterar/excluir
 * produto/variante depois NÃO altera pedidos passados. O {@code color}+{@code dyeLot} do snapshot é o
 * que a regra {@code same_lot_guaranteed} agrupa/valida.
 */
public record LasOrderItem(
    UUID id,
    UUID variantId,
    String productName,
    String color,
    String dyeLot,
    int qtd,
    int unitPriceCents) {
}
