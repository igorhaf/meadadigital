package com.meada.whatsapp.profiles.viagens.proposals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Proposta de viagem (camada 8.18 / perfil viagens) — espelha travel_proposals. Order-based com
 * {@code totalCents} materializado (recalculado a cada mutação de item de COTAÇÃO). Snapshots de
 * cliente. {@code items} (cotação) e {@code itinerary} (roteiro multi-dia) hidratados no
 * findById/detalhe (listas podem vir vazias em listagens leves). DOIS tipos de sub-item no mesmo
 * artefato — não confundir: cotação entra no total; itinerário NÃO.
 *
 * <p>Espelho do EventProposal (chassi eventos 8.2): event_type→destination/travel_style;
 * event_date→start_date/end_date; + num_travelers.
 */
public record TravelProposal(
    UUID id,
    UUID contactId,
    UUID consultantId,
    UUID conversationId,
    String customerName,
    String customerPhone,
    String consultantName,
    String destination,
    LocalDate startDate,
    LocalDate endDate,
    int numTravelers,
    String travelStyle,
    String briefing,
    int totalCents,
    String status,
    String notes,
    Instant openedAt,
    Instant closedAt,
    Instant statusUpdatedAt,
    List<TravelProposalItem> items,
    List<TravelItineraryDay> itinerary) {
}
