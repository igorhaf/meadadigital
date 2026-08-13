package com.meada.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * Documento de conhecimento (PDF) enviado pelo tenant — domínio de knowledge_documents.
 *
 * @param status PROCESSING | READY | FAILED (string, espelha o CHECK do banco; não enum
 *               Java porque é DTO de saída e nenhuma lógica ramifica por ele aqui).
 */
public record KnowledgeDocument(
    UUID id,
    UUID companyId,
    String title,
    String storagePath,
    String status,
    String errorMessage,
    int charCount,
    int chunkCount,
    boolean active,
    Instant deletedAt,
    Instant createdAt,
    Instant updatedAt) {
}
