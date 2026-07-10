package com.meada.profiles.nutri.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant nutri (camada 8.0): janela de funcionamento + buffer. 1:1 com company. Ausente →
 * defaults (08:00/18:00/0).
 */
public record NutriConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int bufferMinutes,
    boolean reminderEnabled,
    boolean autoCompleteEnabled,
    boolean reengagementEnabled,
    int reengagementDays) {

    public static NutriConfig defaultFor(UUID companyId) {
        return new NutriConfig(companyId, LocalTime.of(8, 0), LocalTime.of(18, 0), 0,
            true, true, false, 30);
    }
}
