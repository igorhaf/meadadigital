package com.meada.whatsapp.appointments;

import java.time.Instant;
import java.util.UUID;

/**
 * Compromisso agendado de um contato (camada 5.19 #59/#60) — domínio de {@code appointments}.
 * A IA cria (via AppointmentAction na resposta), o painel exibe no calendário (#59), o
 * ReminderJob manda lembretes (#63), e intents remarcam/cancelam (#64).
 *
 * <p>Carrega o que o painel/calendário e o job de lembrete usam. status do ciclo de vida:
 * scheduled | completed | cancelled | no_show. serviceId/conversationId nullable (nem todo
 * agendamento amarra a um serviço cadastrado ou a uma conversa).
 *
 * @param id             PK
 * @param companyId      tenant
 * @param contactId      cliente do agendamento
 * @param conversationId conversa de origem (nullable; SET NULL on delete)
 * @param serviceId      serviço amarrado (nullable)
 * @param scheduledAt    horário do compromisso (UTC instant)
 * @param status         scheduled | completed | cancelled | no_show
 * @param notes          observações livres; nullable
 * @param reminded24h    lembrete de 24h já enviado (#63)
 * @param reminded2h     lembrete de 2h já enviado (#63)
 * @param createdAt      criação
 */
public record Appointment(
    UUID id,
    UUID companyId,
    UUID contactId,
    UUID conversationId,
    UUID serviceId,
    Instant scheduledAt,
    String status,
    String notes,
    boolean reminded24h,
    boolean reminded2h,
    Instant createdAt) {
}
