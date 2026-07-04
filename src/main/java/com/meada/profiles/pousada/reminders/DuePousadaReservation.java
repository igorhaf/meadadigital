package com.meada.profiles.pousada.reminders;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reserva DUE para o lembrete de check-in / auto-transição (onda Pousada 1, backlog #2/#4).
 * Espelho do DueReservation do restaurant, adaptado ao intervalo de DIAS da pousada.
 */
public record DuePousadaReservation(
    UUID reservationId,
    UUID companyId,
    UUID conversationId,
    String guestName,
    String roomName,
    LocalDate checkInDate,
    LocalDate checkOutDate) {
}
