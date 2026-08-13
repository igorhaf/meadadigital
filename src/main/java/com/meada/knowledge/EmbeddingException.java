package com.meada.knowledge;

/**
 * Falha ao gerar embeddings (sidecar fora do ar, resposta malformada, dim divergente).
 * Não-retentável aqui — propaga para o KnowledgeIngestionService, que marca o documento
 * como FAILED com a mensagem.
 */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
