package com.meada.profiles.otica.config;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Config FUNDIDA do tenant otica (camada 8.12) — espelha {@code otica_config}. 1:1 com company.
 * Funde os dois fluxos do híbrido:
 * <ul>
 *   <li>FLUXO A (exame): {@code opensAt}/{@code closesAt} (janela de funcionamento) +
 *       {@code examDurationMinutes} (duração de um exame — snapshotada em cada exame);</li>
 *   <li>FLUXO B (encomenda): {@code minOrderCents} (pedido mínimo) +
 *       {@code leadTimeDaysDefault} (prazo de montagem padrão quando o item sob encomenda não tem
 *       lead próprio).</li>
 * </ul>
 * Ausente → {@link #defaultFor} (clone do dental_clinic_config + floricultura_config).
 */
public record OticaConfig(
    UUID companyId,
    LocalTime opensAt,
    LocalTime closesAt,
    int examDurationMinutes,
    int minOrderCents,
    int leadTimeDaysDefault,
    boolean examReminderEnabled,
    boolean pickupFollowupEnabled,
    int pickupFollowupDays) {

    /** Defaults cravados (espelha a migration 56): 09:00–18:00, exame 30min, mínimo 0, lead 7 dias. */
    public static OticaConfig defaultFor(UUID companyId) {
        return new OticaConfig(companyId, LocalTime.of(9, 0), LocalTime.of(18, 0), 30, 0, 7, true, true, 3);
    }
}
