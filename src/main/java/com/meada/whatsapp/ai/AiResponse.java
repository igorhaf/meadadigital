package com.meada.whatsapp.ai;

/**
 * Resposta estruturada da IA (structured output). O {@code needsHuman} é o sinal
 * de transferência para humano que sustenta a decisão de handoff.
 *
 * @param reply      texto a enviar ao cliente. NULLABLE. PODE estar populado mesmo
 *                   com {@code needsHuman=true} — nesse caso o OutboundService
 *                   ENVIA este reply ao cliente ANTES de fazer o flip
 *                   handled_by='human' (ex.: "Vou te transferir para um atendente,
 *                   um momento."). UX melhor que silêncio. Quando needsHuman=false,
 *                   reply é a resposta normal e nunca deve ser null/vazio.
 * @param needsHuman true se a IA sinaliza que a conversa precisa de um humano
 *                   (handoff_triggers do tenant, ou a IA não sabe responder).
 * @param reason     motivo do handoff, para log/observabilidade. NULLABLE
 *                   (tipicamente preenchido só quando needsHuman=true).
 * @param tokensIn   tokens do prompt (usageMetadata da API); 0 se indisponível.
 * @param tokensOut  tokens da resposta; 0 se indisponível.
 * @param latencyMs  latência da chamada à IA, medida pelo provider.
 */
public record AiResponse(
    String reply,
    boolean needsHuman,
    String reason,
    int tokensIn,
    int tokensOut,
    long latencyMs) {
}
