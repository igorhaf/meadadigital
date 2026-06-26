package com.meada.whatsapp.profiles.las.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Produto do catálogo las (camada 8.23). DTO de saída. Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProduct} (chassi de varejo): tem
 * {@code category} (id estável de {@link com.meada.whatsapp.profiles.las.LasCategory}) +
 * {@code basePriceCents} (preço base) e a lista de {@link LasVariant variants} (⭐ a grade COR × DYE_LOT
 * — o eixo de variante desta SM). A variante pode SOBREPOR o {@code basePriceCents} via seu próprio
 * {@code priceCents}.
 */
public record LasProduct(
    UUID id,
    String name,
    String description,
    String category,
    int basePriceCents,
    boolean available,
    Instant createdAt,
    Instant updatedAt,
    List<LasVariant> variants) {
}
