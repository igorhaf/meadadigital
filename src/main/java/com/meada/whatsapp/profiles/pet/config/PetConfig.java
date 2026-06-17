package com.meada.whatsapp.profiles.pet.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do pet shop (camada 7.8) — espelha pet_config. Janela de funcionamento + buffer. Ausente →
 * defaults (09:00–19:00).
 */
public record PetConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int bufferMinutes) {

    public static PetConfig defaultFor(UUID companyId) {
        return new PetConfig(companyId, LocalTime.of(9, 0), LocalTime.of(19, 0), 0);
    }
}
