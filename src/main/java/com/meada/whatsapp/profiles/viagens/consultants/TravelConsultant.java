package com.meada.whatsapp.profiles.viagens.consultants;

import java.time.Instant;
import java.util.UUID;

/**
 * Consultor/agente de viagem do tenant viagens (camada 8.18 / perfil viagens) — espelha
 * travel_consultants. {@code specialty} texto livre ("internacional / lua-de-mel", "nacional /
 * cruzeiros"). Catálogo SIMPLES, sem agenda — atribuição opcional na proposta. Espelho EXATO do
 * EventPlanner (chassi eventos 8.2).
 */
public record TravelConsultant(
    UUID id,
    String name,
    String specialty,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
