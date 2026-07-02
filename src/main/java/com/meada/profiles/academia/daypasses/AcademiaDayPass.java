package com.meada.profiles.academia.daypasses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Passe de day-use / aula avulsa da academia (camada 7.7) — espelha academia_day_passes.
 * Só REGISTRO (cobrança real é #50). {@code contactId}/{@code classId} opcionais;
 * {@code guestName}/{@code guestPhone} são snapshots do visitante. {@code paid} nasce false.
 */
public record AcademiaDayPass(
    UUID id,
    UUID contactId,
    String guestName,
    String guestPhone,
    UUID classId,
    LocalDate passDate,
    int priceCents,
    boolean paid,
    Instant createdAt) {
}
