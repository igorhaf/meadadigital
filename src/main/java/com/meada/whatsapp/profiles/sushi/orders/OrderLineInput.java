package com.meada.whatsapp.profiles.sushi.orders;

import java.util.UUID;

/** Linha de pedido na ENTRADA (o que a IA pediu): item do cardápio + quantidade. */
public record OrderLineInput(UUID menuItemId, int qtd) {
}
