package com.meada.profiles.sushi.menu;

import java.time.Instant;
import java.util.UUID;

/**
 * Item de cardápio do perfil sushi (camada 7.1 / sushi funcional). DTO de saída. priceCents em
 * centavos; {@code category} é o UUID (como String) de uma linha em {@code sushi_categories} (FK),
 * ou null (item sem categoria). O antigo enum SushiCategory foi DELETADO — a categoria agora é dado.
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
