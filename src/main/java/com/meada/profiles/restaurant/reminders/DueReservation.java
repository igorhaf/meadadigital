package com.meada.profiles.restaurant.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * Reserva DUE para o lembrete de véspera / auto-transição (onda Restaurant 1, backlog #1/#3).
 * Carrega o mínimo pro job montar o texto e resolver o canal. Espelho do DueBarberAppointment.
 */
public record DueReservation(
    UUID reservationId,
    UUID companyId,
    UUID conversationId,
    String guestName,
    String tableLabel,
    Instant startAt,
    int numPeople) {
}
