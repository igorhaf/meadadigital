package com.meada.whatsapp.profiles.las.orders;

import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil las (camada 8.23): uma VARIANTE
 * (SKU cor × dye_lot) + quantidade. Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.orders.OrderLineInput} (chassi de varejo). O preço e o
 * snapshot são resolvidos no repositório a partir da variante/produto.
 */
public record OrderLineInput(UUID variantId, int qtd) {
}
