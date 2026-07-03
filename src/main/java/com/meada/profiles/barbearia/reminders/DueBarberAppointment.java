package com.meada.profiles.barbearia.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento DUE para o lembrete de confirmação (onda 1, backlog #1): 'agendado', começando nas
 * próximas 24h, ainda não lembrado. Carrega o mínimo pro job montar o "confirma? SIM/CANCELAR" e
 * resolver o canal. Também reusado pela auto-transição (#7) como par (companyId, appointmentId).
 */
public record DueBarberAppointment(
    UUID appointmentId,
    UUID companyId,
    UUID conversationId,
    String guestName,
    String serviceName,
    String barberName,
    Instant startAt) {
}
