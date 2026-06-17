package com.meada.whatsapp.profiles.pousada.rooms;

import java.time.Instant;
import java.util.UUID;

/**
 * Quarto da pousada (camada 7.6) — espelha pousada_rooms. {@code nightlyRateCents} é a diária em
 * centavos; {@code capacity} é o máximo de hóspedes. Ambos entram como snapshot na reserva.
 * {@code active=false} retira da disponibilidade que a IA enxerga.
 */
public record PousadaRoom(
    UUID id,
    String name,
    int capacity,
    int nightlyRateCents,
    String description,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
