package com.meada.profiles.oficina.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * Item do catálogo de materiais/técnicas/serviços do tenant oficina (onda 2, backlog #15). Fonte do
 * AUTOFILL do editor de orçamento (o item da proposta continua snapshot texto) e do upsell da IA
 * (só o nome, sem preço — backlog #10). {@code category} é texto livre (tecido, acabamento, mão de
 * obra...).
 */
public record OficinaCatalogItem(
    UUID id,
    UUID companyId,
    String name,
    String category,
    int unitPriceCents,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
