package com.meada.whatsapp.profiles.las.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * ⭐ Variante (COR × DYE_LOT) de um produto las (camada 8.23, chassi de varejo) — o SKU real. DTO de
 * saída. Clone do {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieVariant} com o eixo de
 * variante TROCADO: a lingerie é tamanho×cor; aqui é {@code color} (texto livre) × {@code dyeLot}
 * (LOTE DE TINGIMENTO, texto livre, ex.: "L2024-A"). Novelos da mesma cor de lotes diferentes têm
 * variação visível de tom — por isso cada (cor, lote) é um SKU próprio com SEU estoque.
 *
 * <p>{@code priceCents} é NULLABLE — quando null, a variante herda o {@code basePriceCents} do
 * produto. {@code stockQty} é o estoque disponível; o pedido o DECREMENTA transacionalmente na
 * criação. {@code color} e {@code dyeLot} são texto livre (sem enum).
 */
public record LasVariant(
    UUID id,
    UUID productId,
    String color,
    String dyeLot,
    String sku,
    Integer priceCents,
    int stockQty,
    boolean available,
    Instant createdAt,
    Instant updatedAt) {
}
