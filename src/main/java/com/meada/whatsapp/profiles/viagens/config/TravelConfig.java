package com.meada.whatsapp.profiles.viagens.config;

import java.util.UUID;

/**
 * Config do tenant viagens (camada 8.18 / perfil viagens): nome da agência + notas. SEM horário/slot —
 * a proposta é order-based, não agendada por horário (start_date/end_date são campos LIVRES). 1:1 com
 * company. Ausente → defaults (vazios). Espelho EXATO do EventConfig (chassi eventos 8.2).
 */
public record TravelConfig(
    UUID companyId,
    String businessName,
    String notes) {

    public static TravelConfig defaultFor(UUID companyId) {
        return new TravelConfig(companyId, null, null);
    }
}
