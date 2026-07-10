package com.meada.profiles.eventos.packages;

import java.time.Instant;
import java.util.UUID;

/**
 * Pacote/adicional do catálogo do buffet (onda Eventos 1, backlog #2). {@code kind} pacote|
 * adicional; {@code suggestible} entra no upsell consultivo da IA (#9 — só o que está
 * cadastrado). O item da proposta continua snapshot texto+preço (autofill no editor).
 */
public record EventPackage(
    UUID id,
    String name,
    String kind,
    String description,
    int priceCents,
    boolean suggestible,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
