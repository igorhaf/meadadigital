package com.meada.whatsapp.profiles.atelie.config;

import java.util.UUID;

/**
 * Config do tenant atelie (camada 8.14): nome do ateliê/estúdio + notas. SEM horário/slot — a
 * proposta é order-based, não agendada por horário. 1:1 com company. Ausente → defaults (vazios).
 * Espelho do EventConfig.
 */
public record AtelieConfig(
    UUID companyId,
    String businessName,
    String notes) {

    public static AtelieConfig defaultFor(UUID companyId) {
        return new AtelieConfig(companyId, null, null);
    }
}
