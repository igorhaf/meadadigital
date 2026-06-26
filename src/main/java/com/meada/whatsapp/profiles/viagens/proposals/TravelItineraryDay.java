package com.meada.whatsapp.profiles.viagens.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Dia do ROTEIRO/ITINERÁRIO de uma proposta de viagem (camada 8.18 / perfil viagens) — espelha
 * travel_itinerary_days. A ESCAPADA da SM: roteiro MULTI-DIA — UMA linha por DIA da viagem
 * ({@code dayNumber} int + {@code dayDate} date NULLABLE), ordenado por day_date asc NULLS LAST,
 * day_number asc, created_at asc. Cobre 7/10/15 dias.
 *
 * <p>DIFERENTE do CRONOGRAMA do eventos ({@code EventTimelineItem}, que é UM dia ordenado por HORA):
 * aqui são MÚLTIPLOS dias ordenados por DATA. SEM status (descritivo). NÃO entra no {@code totalCents}
 * da proposta. Gerenciado SÓ no painel (sem tag de IA). Trava junto com {@code itemsLocked()}.
 */
public record TravelItineraryDay(
    UUID id,
    UUID proposalId,
    int dayNumber,
    LocalDate dayDate,
    String title,
    String description,
    Instant createdAt,
    Instant updatedAt) {
}
