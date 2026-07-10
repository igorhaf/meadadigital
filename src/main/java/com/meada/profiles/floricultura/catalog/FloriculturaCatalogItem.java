package com.meada.profiles.floricultura.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item de cardápio do perfil floricultura (camada 8.4). DTO de saída. Clone de
 * {@link com.meada.profiles.sushi.catalog.SushiCatalogItem} + a lista de {@code options}
 * (ESCAPADA 2: modifiers). priceCents em centavos é o preço BASE (sem opções); category é o id
 * estável de {@link com.meada.profiles.floricultura.FloriculturaCategory}.
 */
public record FloriculturaCatalogItem(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    boolean available,
    boolean suggestible,
    Instant createdAt,
    Instant updatedAt,
    List<FloriculturaCatalogOption> options) {
}
