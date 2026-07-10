package com.meada.profiles.papelaria.catalog;

/**
 * Faixa de preço por TIRAGEM de um item do catálogo (onda Papelaria 1, backlog #2): a faixa com o
 * MAIOR {@code minQty} &le; quantity define o unit_price-base da linha do pedido. Ordenadas por
 * minQty; sem faixas → unit_price do item.
 */
public record PapelariaItemTier(int minQty, int unitPriceCents) {
}
