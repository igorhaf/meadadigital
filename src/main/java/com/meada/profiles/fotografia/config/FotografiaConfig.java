package com.meada.profiles.fotografia.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant fotografia (camada 8.16): janela de funcionamento + granularidade de slot. 1:1 com
 * company. Ausente → defaults (08:00/20:00/30). SEM duration — a duração da sessão vem do pacote
 * (snapshot). Espelho do DermatologiaConfig, com {@code slotMinutes} no lugar de buffer.
 */
public record FotografiaConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int slotMinutes,
    boolean reminderEnabled,
    boolean autoCompleteEnabled,
    boolean autoDeliverEnabled,
    boolean postDeliveryUpsellEnabled,
    Integer cancellationPolicyHours) {

    public static FotografiaConfig defaultFor(UUID companyId) {
        return new FotografiaConfig(companyId, LocalTime.of(8, 0), LocalTime.of(20, 0), 30,
            true, true, true, true, null);
    }
}
