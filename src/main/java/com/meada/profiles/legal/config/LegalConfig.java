package com.meada.profiles.legal.config;

import java.util.UUID;

/**
 * Config 1:1 do tenant legal (onda 1): link de avaliação (Google) + toggles do pós-encerramento
 * e do lembrete de prazos. Ausente → defaults (toggles ligados, sem link).
 */
public record LegalConfig(
    UUID companyId,
    String reviewLink,
    boolean postClosureEnabled,
    boolean deadlineReminderEnabled) {

    public static LegalConfig defaultFor(UUID companyId) {
        return new LegalConfig(companyId, null, true, true);
    }
}
