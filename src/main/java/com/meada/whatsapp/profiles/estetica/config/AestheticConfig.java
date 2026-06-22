package com.meada.whatsapp.profiles.estetica.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant estetica (camada 8.3) — espelha aesthetic_config. Janela de funcionamento +
 * granularidade de slot. Ausente → defaults (09:00–19:00, slot 30).
 */
public record AestheticConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int slotMinutes) {

    public static AestheticConfig defaultFor(UUID companyId) {
        return new AestheticConfig(companyId, LocalTime.of(9, 0), LocalTime.of(19, 0), 30);
    }
}
