package com.meada.whatsapp.profiles.suplementos.catalog;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ⭐ Variante (sabor × peso/tamanho) de um produto suplementos (camada 8.24, ESCAPADA 1 — chassi de
 * varejo do {@link com.meada.whatsapp.profiles.lingerie.catalog.LingerieVariant}) — o SKU real. DTO de
 * saída. Diferente do lingerie (onde priceCents era nullable e herdava o base), aqui {@code priceCents}
 * é o preço DA VARIANTE (NOT NULL na migration — cada sabor×peso tem seu próprio preço).
 *
 * <p>{@code flavor} é NULLABLE (acessório/cápsula sem sabor); {@code sizeLabel} é o peso/tamanho
 * ('900g', '2kg', '120 caps', '600ml'). {@code stockQuantity} é o estoque disponível; o pedido o
 * DECREMENTA transacionalmente na criação (espelho do decrementStock do lingerie). {@code expiryDate}
 * é administrativo informativo (a IA NÃO promete validade).
 */
public record SupVariant(
    UUID id,
    UUID productId,
    String flavor,
    String sizeLabel,
    String sku,
    int priceCents,
    int stockQuantity,
    LocalDate expiryDate,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

    /** Rótulo legível da variante: "Chocolate 900g" (flavor + sizeLabel), ou só o sizeLabel se sem sabor. */
    public String label() {
        if (flavor != null && !flavor.isBlank()) {
            return flavor.strip() + " " + sizeLabel;
        }
        return sizeLabel;
    }
}
