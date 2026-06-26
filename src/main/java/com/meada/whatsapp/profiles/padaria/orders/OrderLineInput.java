package com.meada.whatsapp.profiles.padaria.orders;

import java.util.List;
import java.util.UUID;

/**
 * Linha de pedido na ENTRADA (o que a IA pediu) no perfil padaria (camada 8.8 / perfil padaria): item
 * do cardápio + quantidade + ids das opções escolhidas (ESCAPADA 2) + a mensagem da placa do bolo
 * (ESCAPADA 2: {@code cakeMessage}, nullable, snapshot). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.OrderLineInput} + {@code cakeMessage}. Item
 * sem opção → lista vazia; item sem placa → cakeMessage null.
 */
public record OrderLineInput(UUID menuItemId, int qtd, List<UUID> optionIds, String cakeMessage) {
}
