package com.meada.profiles.salon.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento DUE para o lembrete de véspera (onda Salon 1, backlog #1): agendado/confirmado com
 * start_at amanhã e ainda não lembrado para ESSE horário (remarcar rearma). Carrega o mínimo pro
 * job montar o texto (snapshots) e resolver o canal.
 */
public record DueSalonAppointment(
    UUID appointmentId,
    UUID companyId,
    UUID conversationId,
    String guestName,
    String professionalName,
    String serviceName,
    Instant startAt) {
}
