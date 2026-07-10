package com.meada.profiles.otica.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * Item DUE dos disparos da ótica (onda 1, backlog #1/#2): exame de amanhã a lembrar OU pedido
 * parado em 'pronto' a cutucar. Carrega o mínimo pro texto + canal.
 */
public record DueOticaWork(
    UUID id,
    UUID companyId,
    UUID conversationId,
    String customerName,
    String professionalName,
    Instant startAt) {
}
