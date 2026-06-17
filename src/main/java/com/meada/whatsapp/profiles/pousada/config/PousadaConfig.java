package com.meada.whatsapp.profiles.pousada.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config da pousada (camada 7.6) — espelha pousada_config. {@code checkInTime}/{@code checkOutTime}
 * são os horários de entrada/saída; {@code cancellationPolicy} é texto livre opcional. Ausente →
 * defaults (14:00/11:00/null).
 */
public record PousadaConfig(
    UUID companyId,
    LocalTime checkInTime,
    LocalTime checkOutTime,
    String cancellationPolicy) {

    public static PousadaConfig defaultFor(UUID companyId) {
        return new PousadaConfig(companyId, LocalTime.of(14, 0), LocalTime.of(11, 0), null);
    }
}
