package com.meada.whatsapp.profiles.padaria.menu;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Item de cardápio do perfil padaria (camada 8.8 / perfil padaria). DTO de saída. Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.catalog.FloriculturaCatalogItem} + a ESCAPADA 1
 * ({@code madeToOrder} + {@code leadTimeDays} nullable que sobrepõe o default da config) +
 * {@code allergens} (texto livre informativo). priceCents em centavos é o preço BASE (sem opções);
 * category é o id estável de {@link com.meada.whatsapp.profiles.padaria.PadariaCategory}.
 */
public record PadariaMenuItem(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    boolean madeToOrder,
    Integer leadTimeDays,
    String allergens,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<PadariaMenuOption> options) {
}
