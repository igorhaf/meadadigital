package com.meada.whatsapp.profiles.suplementos.orders;

import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil suplementos (camada 8.24): uma VARIANTE
 * (SKU sabor×peso) + quantidade. Análogo ao
 * {@link com.meada.whatsapp.profiles.lingerie.orders.OrderLineInput} (chassi de varejo). O preço e o
 * snapshot são resolvidos no repositório a partir da variante/produto.
 */
public record OrderLineInput(UUID variantId, int qtd) {
}
