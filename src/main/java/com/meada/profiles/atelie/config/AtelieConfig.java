package com.meada.profiles.atelie.config;

import java.util.UUID;

/**
 * Config do tenant atelie (camada 8.14): nome do ateliê/estúdio + notas + toggle do lembrete de
 * prova/ajuste (onda backlog #1). SEM horário/slot — a proposta é order-based, não agendada por
 * horário. 1:1 com company. Ausente → defaults (vazios, lembrete LIGADO). Espelho do EventConfig.
 */
public record AtelieConfig(
    UUID companyId,
    String businessName,
    String notes,
    boolean fittingReminderEnabled,
    boolean postDeliveryEnabled,
    String reviewLink,
    boolean reactivationEnabled,
    int reactivationDays) {

    public static AtelieConfig defaultFor(UUID companyId) {
        return new AtelieConfig(companyId, null, null, true, true, null, false, 90);
    }
}
