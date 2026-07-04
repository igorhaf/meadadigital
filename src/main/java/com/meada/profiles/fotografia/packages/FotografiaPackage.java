package com.meada.profiles.fotografia.packages;

import java.time.Instant;
import java.util.UUID;

/**
 * Pacote do tenant fotografia (camada 8.16) — espelha fotografia_packages. Catálogo de pacotes
 * (preço + duração + delivery_days). {@code durationMinutes} entra como snapshot na sessão (a duração
 * vem do pacote, NÃO de config). {@code priceCents} é o preço do pacote (a IA não inventa preço).
 * {@code deliveryDays} é o prazo de entrega após a sessão (vira delivery_due_date materializada).
 * Espelho leve do DermatologiaProcedureType / aesthetic_procedures SEM saldo multi-sessão.
 */
public record FotografiaPackage(
    UUID id,
    String name,
    String category,
    int durationMinutes,
    int priceCents,
    int deliveryDays,
    boolean active,
    boolean suggestible,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
