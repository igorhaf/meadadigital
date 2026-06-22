package com.meada.whatsapp.profiles.estetica.procedures;

import java.time.Instant;
import java.util.UUID;

/**
 * Procedimento do tenant estetica (camada 8.3) — espelha aesthetic_procedures. {@code unitPriceCents}
 * é o preço de UMA sessão (o pacote = total_sessions * unit_price; a IA não inventa preço).
 * {@code durationMinutes} entra como snapshot no agendamento. Espelho de SalonOffering + preço
 * obrigatório.
 */
public record AestheticProcedure(
    UUID id,
    String name,
    String category,
    int durationMinutes,
    int unitPriceCents,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
