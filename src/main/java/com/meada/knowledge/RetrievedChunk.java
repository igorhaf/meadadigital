package com.meada.knowledge;

/**
 * Chunk recuperado no retrieval semântico, com a similaridade cosine com a consulta.
 * Consumido pelo PromptBuilder (5.13.d) para montar o bloco {{knowledge}} do prompt.
 */
public record RetrievedChunk(
    String documentTitle,
    int chunkIndex,
    String content,
    double similarity) {
}
