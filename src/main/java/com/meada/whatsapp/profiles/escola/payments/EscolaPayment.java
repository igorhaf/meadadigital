package com.meada.whatsapp.profiles.escola.payments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mensalidade manual (camada 8.19) — espelha escola_payments. {@code referenceMonth} é sempre o dia
 * 01 do mês de referência. {@code method} texto livre. UNIQUE (enrollment, referenceMonth).
 */
public record EscolaPayment(
    UUID id,
    UUID enrollmentId,
    LocalDate referenceMonth,
    Instant paidAt,
    int amountCents,
    String method,
    String notes) {
}
