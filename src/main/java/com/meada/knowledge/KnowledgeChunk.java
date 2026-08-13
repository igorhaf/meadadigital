package com.meada.knowledge;

import java.util.UUID;

/**
 * Um chunk de texto de um documento + seu embedding (384-dim). Persistido em
 * knowledge_chunks; o embedding vai como vector(384) via ?::vector no INSERT.
 */
public record KnowledgeChunk(
    UUID id,
    UUID documentId,
    UUID companyId,
    int chunkIndex,
    String content,
    float[] embedding) {
}
