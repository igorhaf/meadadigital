package com.meada.profiles.eventos.config;

import java.util.UUID;

/**
 * Config do tenant eventos (camada 8.2): nome do espaço/buffet + notas. SEM horário/slot — a
 * proposta é order-based, não agendada por horário. 1:1 com company. Ausente → defaults (vazios).
 * Espelho LEVE do OficinaConfig (que tinha horário; aqui não há agenda).
 */
public record EventConfig(
    UUID companyId,
    String businessName,
    String notes,
    boolean autoCompleteEnabled,
    boolean postEventEnabled,
    String reviewLink,
    boolean followUpEnabled,
    int followUpDays) {

    public static EventConfig defaultFor(UUID companyId) {
        return new EventConfig(companyId, null, null, true, true, null, true, 3);
    }
}
