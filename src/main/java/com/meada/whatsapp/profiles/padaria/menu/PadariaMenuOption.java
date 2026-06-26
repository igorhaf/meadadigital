package com.meada.whatsapp.profiles.padaria.menu;

import java.time.Instant;
import java.util.UUID;

/**
 * Opção/personalização (modifier) de um item do cardápio padaria (camada 8.8 / perfil padaria,
 * ESCAPADA 2). Cada linha é UMA opção de UM grupo ({@code groupLabel} agrupa no app: "Sabor",
 * "Recheio", "Tamanho"). DTO de saída. {@code priceDeltaCents} soma ao preço base no pedido (recálculo
 * no backend). Clone de {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogOption}
 * (catalog→menu: a coluna FK é {@code menu_item_id}).
 */
public record PadariaMenuOption(
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
