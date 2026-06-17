package com.meada.whatsapp.profiles.pousada.reservations;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Conflito de reserva (camada 7.6): a reserva existente (reservado/confirmado/checked_in) do MESMO
 * QUARTO cujo intervalo de dias sobrepõe o novo. Devolvido por
 * {@link PousadaReservationRepository#findConflict} e exposto no 409 conflict_dates.
 */
public record PousadaReservationConflict(
    UUID existingId,
    String existingGuestName,
    LocalDate existingCheckInDate,
    LocalDate existingCheckOutDate,
    String existingRoomName) {
}
