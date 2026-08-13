package com.meada.profiles.sushi.categories;

import java.time.Instant;
import java.util.UUID;

/**
 * Categoria de cardápio do tenant sushi (camada 7.1 / sushi funcional). DTO de saída — substitui
 * o antigo enum {@code SushiCategory} (que foi DELETADO). Agora as categorias são dados gerenciáveis
 * pelo tenant em {@code sushi_categories}; {@code sushi_menu_items.category} é FK p/ esta tabela.
 */
public record SushiCategoryEntity(
    UUID id,
    UUID companyId,
    String name,
    int sortOrder,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
