package com.meada.profiles.barbearia.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config da barbearia (camada 8.1) — espelha barber_config. {@code opensAt}/{@code closesAt} é a
 * janela de funcionamento; {@code slotMinutes} é a granularidade dos slots livres que a IA enxerga;
 * {@code queueEnabled} liga/desliga a fila de walk-in. Onda 1 do backlog: {@code reminderEnabled}
 * (lembrete "confirma? SIM/CANCELAR" nas 24h — #1), {@code autoCompleteEnabled} (auto-transição
 * confirmado passado → realizado + expira fila de dias anteriores — #7) e {@code upsellEnabled}
 * (a IA pode sugerir UMA vez um complemento do catálogo — #4, opt-in). Ausente → defaults
 * (09:00–20:00, 15, true, true, true, false).
 */
public record BarberConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int slotMinutes,
    boolean queueEnabled,
    boolean reminderEnabled,
    boolean autoCompleteEnabled,
    boolean upsellEnabled) {

    /** Defaults cravados: 09:00–20:00, slot 15min, fila ligada, lembrete/auto-transição ligados, upsell OFF. */
    public static BarberConfig defaultFor(UUID companyId) {
        return new BarberConfig(companyId, LocalTime.of(9, 0), LocalTime.of(20, 0), 15, true, true, true, false);
    }
}
