package com.meada.whatsapp.profiles.academia.payments;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Pagamento manual mensal (camada 7.7) — espelha academia_payments. {@code referenceMonth} é sempre
 * o dia 01 do mês de referência. {@code method} texto livre. UNIQUE (membership, referenceMonth).
 */
public record AcademiaPayment(
    UUID id,
    UUID membershipId,
    LocalDate referenceMonth,
    Instant paidAt,
    int amountCents,
    String method,
    String notes) {
}
