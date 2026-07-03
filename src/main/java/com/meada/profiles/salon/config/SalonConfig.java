package com.meada.profiles.salon.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do salão (camada 7.5) — espelha salon_config. {@code opensAt}/{@code closesAt} é a janela de
 * funcionamento; {@code bufferMinutes} é o intervalo extra entre agendamentos (0 nesta SM). Ausente →
 * defaults (09:00–20:00).
 */
public record SalonConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int bufferMinutes,
    boolean reminderEnabled,
    boolean autoCompleteEnabled) {

    /** Defaults cravados (decisão): 09:00–20:00, sem buffer; lembrete ON, auto-transição OFF. */
    public static SalonConfig defaultFor(UUID companyId) {
        return new SalonConfig(companyId, LocalTime.of(9, 0), LocalTime.of(20, 0), 0, true, false);
    }
}
