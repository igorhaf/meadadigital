package com.meada.profiles.concessionaria.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config do tenant concessionaria (camada 8.17) — espelha concessionaria_config. Clone do
 * dental_clinic_config + {@code businessName} (estilo event_config). {@code durationMinutes} é quanto
 * dura um test-drive (45min padrão); {@code bufferMinutes} é o intervalo extra (0 nesta SM);
 * {@code opensAt}/{@code closesAt} é a janela de funcionamento. ONDA 1 do backlog: toggles do
 * follow-up de lead parado (#2, com followupDays), do lembrete de test-drive (#3) e do
 * auto-realizado (#9). Ausente → defaults (automações LIGADAS).
 */
public record ConcessionariaConfig(
    UUID companyId,
    String businessName,
    int durationMinutes,
    int bufferMinutes,
    LocalTime opensAt,
    LocalTime closesAt,
    String notes,
    boolean followupEnabled,
    int followupDays,
    boolean testdriveReminderEnabled,
    boolean autoCompleteEnabled,
    boolean postSaleEnabled,
    String reviewLink,
    boolean serviceReminderEnabled,
    int serviceReminderMonths) {

    /** Defaults cravados: 45min de test-drive, sem buffer, 09:00–18:00, automações da onda 1 LIGADAS. */
    public static ConcessionariaConfig defaultFor(UUID companyId) {
        return new ConcessionariaConfig(
            companyId, null, 45, 0, LocalTime.of(9, 0), LocalTime.of(18, 0), null, true, 3, true, true,
            true, null, false, 12);
    }
}
