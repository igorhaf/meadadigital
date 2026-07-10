package com.meada.profiles.dermatologia.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant dermatologia (camada 8.11): janela de funcionamento + buffer. 1:1 com company.
 * Ausente → defaults (08:00/18:00/0). SEM duration — a duração vem do procedure_type (escapada).
 */
public record DermatologiaConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int bufferMinutes,
    boolean reminderEnabled,
    boolean autoCompleteEnabled,
    boolean recallEnabled,
    int recallMonths) {

    public static DermatologiaConfig defaultFor(UUID companyId) {
        return new DermatologiaConfig(companyId, LocalTime.of(8, 0), LocalTime.of(18, 0), 0,
            true, true, false, 6);
    }
}
