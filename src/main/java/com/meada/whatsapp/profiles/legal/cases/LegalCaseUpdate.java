package com.meada.whatsapp.profiles.legal.cases;

import java.time.Instant;
import java.util.UUID;

/** Andamento manual de um processo (camada 7.2). occurred_at pode ser passado. */
public record LegalCaseUpdate(
    UUID id,
    String title,
    String body,
    Instant occurredAt,
    Instant createdAt) {
}
