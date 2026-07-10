package com.meada.profiles.oficina.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant oficina (camada 7.9): horário INFORMATIVO de funcionamento (sem lógica de slot —
 * a OS é order-based, não agendada por horário). 1:1 com company. Ausente → defaults (08:00/18:00).
 */
public record OficinaConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    boolean returnReminderEnabled,
    int returnReminderDays) {

    public static OficinaConfig defaultFor(UUID companyId) {
        return new OficinaConfig(companyId, LocalTime.of(8, 0), LocalTime.of(18, 0), true, 180);
    }
}
