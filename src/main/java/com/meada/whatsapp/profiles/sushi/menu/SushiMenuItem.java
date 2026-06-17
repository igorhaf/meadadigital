package com.meada.whatsapp.profiles.sushi.menu;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de cardápio do perfil sushi (camada 7.1). DTO de saída. priceCents em centavos;
 * category é o id estável de {@link com.meada.whatsapp.profiles.sushi.SushiCategory}.
 */
public record SushiMenuItem(
    UUID id,
    String name,
    String description,
    int priceCents,
    String category,
    boolean available,
    Instant createdAt,
    Instant updatedAt) {
}
