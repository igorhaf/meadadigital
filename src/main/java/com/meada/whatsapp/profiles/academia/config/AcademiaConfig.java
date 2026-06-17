package com.meada.whatsapp.profiles.academia.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config da academia (camada 7.7) — espelha academia_config. Horário de funcionamento. Ausente →
 * defaults (06:00–22:00).
 */
public record AcademiaConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt) {

    public static AcademiaConfig defaultFor(UUID companyId) {
        return new AcademiaConfig(companyId, LocalTime.of(6, 0), LocalTime.of(22, 0));
    }
}
