package com.meada.profiles.casamento.config;

import java.util.UUID;

/**
 * Config do tenant casamento (camada 8.7): nome da assessoria + notas + toggles da onda 1 do backlog
 * (lembrete de checklist #2, lembrete de parcela #1/#2, auto-realizada #4, aniversário #16). SEM
 * horário/slot — a proposta é order-based, não agendada por horário. 1:1 com company. Ausente →
 * defaults (vazios, toggles LIGADOS). Espelho do EventConfig.
 */
public record WeddingConfig(
    UUID companyId,
    String businessName,
    String notes,
    boolean checklistReminderEnabled,
    boolean paymentReminderEnabled,
    boolean autoCompleteEnabled,
    boolean anniversaryEnabled,
    boolean postEventEnabled,
    String reviewLink,
    boolean followUpEnabled,
    int followUpDays) {

    public static WeddingConfig defaultFor(UUID companyId) {
        return new WeddingConfig(companyId, null, null, true, true, true, true, true, null, true, 5);
    }
}
