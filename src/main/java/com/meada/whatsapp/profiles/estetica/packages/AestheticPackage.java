package com.meada.whatsapp.profiles.estetica.packages;

import java.time.Instant;
import java.util.UUID;

/**
 * Pacote multi-sessão de estética (camada 8.3) — espelha aesthetic_packages. A ESCAPADA da SM:
 * saldo pré-pago consumível. {@code sessionsRemaining} e {@code totalCents} são MATERIALIZADOS.
 * Snapshots de cliente + procedimento + preço unitário. Status pendente→ativo→esgotado/expirado/
 * cancelado.
 */
public record AestheticPackage(
    UUID id,
    UUID contactId,
    UUID procedureId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    String procedureName,
    int unitPriceCents,
    int totalSessions,
    int sessionsUsed,
    int sessionsRemaining,
    int totalCents,
    String status,
    String notes,
    Instant purchasedAt,
    Instant activatedAt,
    Instant statusUpdatedAt) {
}
