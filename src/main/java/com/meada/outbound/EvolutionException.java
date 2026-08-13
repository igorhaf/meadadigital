package com.meada.outbound;

/**
 * Falha ao enviar mensagem pela Evolution API. Unchecked (RuntimeException) para
 * não poluir a cadeia de assinaturas — o OutboundService captura explicitamente
 * no ponto do retry/flip. Espelha {@code com.meada.ai.AiException}.
 *
 * <p>Esta é a falha FATAL (não-retentável): HTTP 4xx (401 token inválido, 404
 * instância inexistente) ou erro de parse da resposta. Pela matriz de fluxo do
 * OutboundService (caso 8), erro fatal de envio → log ERROR, SEM flip humano (o
 * canal está quebrado, atendente humano usaria o mesmo canal) — config do tenant
 * precisa ser corrigida operacionalmente.
 *
 * <p>Para falhas TRANSIENTES (retentáveis), ver {@link EvolutionTransientException}.
 */
public class EvolutionException extends RuntimeException {

    public EvolutionException(String message) {
        super(message);
    }

    public EvolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
