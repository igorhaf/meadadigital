package com.meada.profiles.pet.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * Agendamento DUE para o lembrete de véspera (onda Pet 1, backlog #1). Carrega o mínimo pro job
 * montar o texto carinhoso (nome do pet + serviço + profissional) e resolver o canal do tutor.
 */
public record DuePetAppointment(
    UUID appointmentId,
    UUID companyId,
    UUID conversationId,
    String tutorName,
    String animalName,
    String serviceName,
    String professionalName,
    Instant startAt) {
}
