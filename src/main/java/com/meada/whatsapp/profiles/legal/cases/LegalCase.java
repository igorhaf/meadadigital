package com.meada.whatsapp.profiles.legal.cases;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Processo (camada 7.2). DTO de saída. cnjNumber é storage (20 dígitos); cnjNumberFormatted é
 * a máscara. legalClientName vem do join. updates inclusos no detalhe (vazio na listagem).
 */
public record LegalCase(
    UUID id,
    UUID legalClientId,
    String legalClientName,
    String cnjNumber,
    String cnjNumberFormatted,
    String title,
    String description,
    String court,
    String forum,
    String subject,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant statusUpdatedAt,
    int updatesCount,
    List<LegalCaseUpdate> updates) {
}
