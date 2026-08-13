package com.meada.ai;

/**
 * Falha ao gerar resposta com a IA. Unchecked (RuntimeException) para não poluir
 * a cadeia de assinaturas com {@code throws} — o OutboundService (Fase 3.3) captura
 * explicitamente no ponto do retry/flip.
 *
 * <p>Esta é a falha FATAL (não-retentável): HTTP 4xx (prompt inválido, chave
 * inválida) ou erro de parse da resposta. O OutboundService trata como
 * "IA não conseguiu responder" → flip handled_by='human'.
 *
 * <p>Para falhas TRANSIENTES (retentáveis), ver {@link AiTransientException}.
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
