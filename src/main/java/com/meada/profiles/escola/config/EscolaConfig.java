package com.meada.profiles.escola.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config da escola (camada 8.19) — espelha escola_config. Nome da escola + horário de funcionamento
 * + notes. Ausente → defaults (07:00–18:00).
 */
public record EscolaConfig(
    UUID companyId,
    String businessName,
    LocalTime opensAt,
    LocalTime closesAt,
    String notes,
    boolean visitReminderEnabled,
    boolean visitAutoCompleteEnabled,
    boolean paymentReminderEnabled,
    int paymentDueDay) {

    public static EscolaConfig defaultFor(UUID companyId) {
        return new EscolaConfig(companyId, null, LocalTime.of(7, 0), LocalTime.of(18, 0), null,
            true, true, false, 10);
    }
}
