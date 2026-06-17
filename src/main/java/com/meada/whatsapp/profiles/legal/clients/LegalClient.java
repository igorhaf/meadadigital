package com.meada.whatsapp.profiles.legal.clients;

import java.time.Instant;
import java.util.UUID;

/**
 * Cliente do escritório (camada 7.2). DTO de saída. name obrigatório; demais opcionais.
 * contactId liga ao contato do WhatsApp (nullable).
 */
public record LegalClient(
    UUID id,
    String name,
    String email,
    String phone,
    String document,
    UUID contactId,
    String notes,
    Instant createdAt,
    Instant updatedAt) {
}
