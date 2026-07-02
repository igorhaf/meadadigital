package com.meada.profiles.academia.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * Saldo de pontos de fidelidade de um contato (camada 7.7, feature #12) — espelha academia_loyalty.
 * PK lógica (companyId, contactId). {@code points} nunca é negativo. Ausência de linha = 0 pontos.
 */
public record AcademiaLoyaltyBalance(
    UUID companyId,
    UUID contactId,
    int points,
    Instant updatedAt) {

    public static AcademiaLoyaltyBalance zeroFor(UUID companyId, UUID contactId) {
        return new AcademiaLoyaltyBalance(companyId, contactId, 0, null);
    }
}
