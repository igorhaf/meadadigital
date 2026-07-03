package com.meada.profiles.comida.zones;

import java.time.Instant;
import java.util.UUID;

/**
 * Zona de entrega com taxa própria do tenant comida (onda 1, backlog #8). A taxa FLAT da config é o
 * FALLBACK: a IA escolhe a zona da lista do contexto e passa {@code zona_id} na tag; zona ausente/
 * inválida/inativa → flat (nunca aborta).
 */
public record ComidaDeliveryZone(
    UUID id,
    UUID companyId,
    String name,
    int feeCents,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {
}
