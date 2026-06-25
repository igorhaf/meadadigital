package com.meada.whatsapp.profiles.casamento.config;

import java.util.UUID;

/**
 * Config do tenant casamento (camada 8.7): nome da assessoria + notas. SEM horário/slot — a proposta
 * é order-based, não agendada por horário. 1:1 com company. Ausente → defaults (vazios). Espelho do
 * EventConfig.
 */
public record WeddingConfig(
    UUID companyId,
    String businessName,
    String notes) {

    public static WeddingConfig defaultFor(UUID companyId) {
        return new WeddingConfig(companyId, null, null);
    }
}
