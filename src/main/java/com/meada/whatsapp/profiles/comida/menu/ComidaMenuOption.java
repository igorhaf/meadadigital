package com.meada.whatsapp.profiles.comida.menu;

import java.time.Instant;
import java.util.UUID;

/**
 * Opção/adicional (modifier) de um item do cardápio comida (camada 8.4, ESCAPADA 2). Cada linha é
 * UMA opção de UM grupo ({@code groupLabel} agrupa no app: "Tamanho", "Adicionais"). DTO de saída.
 * {@code priceDeltaCents} soma ao preço base no pedido (recálculo no backend). Não há paralelo no
 * sushi — é a sub-entidade nova deste perfil.
 */
public record ComidaMenuOption(
    UUID id,
    UUID menuItemId,
    String groupLabel,
    String optionLabel,
    int priceDeltaCents,
    boolean available,
    int sortOrder,
    Instant createdAt,
    Instant updatedAt) {
}
