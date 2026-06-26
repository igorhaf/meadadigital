package com.meada.whatsapp.profiles.suplementos.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Produto do catálogo suplementos (camada 8.24). DTO de saída. Análogo ao
 * {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieProduct} (chassi de varejo), mas com
 * {@code brand} (marca) em vez de {@code basePriceCents} — aqui o preço mora SEMPRE na variante (não
 * há preço base no produto). {@code description} é informativo de PRODUTO, NÃO dosagem/posologia (a IA
 * não usa pra recomendar — trava de saúde). Carrega a lista de {@link SupVariant variants} (⭐ a grade
 * sabor×peso, o SKU vendável).
 */
public record SupProduct(
    UUID id,
    String name,
    String brand,
    String category,
    String description,
    boolean active,
    Instant createdAt,
    Instant updatedAt,
    List<SupVariant> variants) {
}
