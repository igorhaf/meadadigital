package com.meada.whatsapp.profiles.pousada.reservations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Reserva de pousada (camada 7.6) — espelha pousada_reservations. {@code checkInDate}/
 * {@code checkOutDate} são DIAS (LocalDate, não instante). {@code nights}/{@code totalCents}
 * materializados; {@code roomName}/{@code nightlyRateCents}/{@code capacitySnapshot} snapshots do
 * momento. {@code conversationId}/{@code contactId} nullable (reserva manual). {@code notes}
 * administrativo (LGPD).
 */
public record PousadaReservation(
    UUID id,
    UUID roomId,
    String roomName,
    UUID conversationId,
    UUID contactId,
    String guestName,
    String guestPhone,
    int guestsCount,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    int nights,
    int nightlyRateCents,
    int capacitySnapshot,
    int totalCents,
    String status,
    String notes,
    Instant createdAt,
    Instant statusUpdatedAt) {
}
