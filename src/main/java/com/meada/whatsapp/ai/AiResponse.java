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
 * @param schedulingIntent intenção de agendamento detectada na mensagem do cliente
 *                   (camada 5.15 #29); NULLABLE — null quando o modelo não detectou
 *                   intenção de marcar/agendar (caso da maioria das mensagens). Quando
 *                   presente, o OutboundService persiste em conversations.scheduling_intent
 *                   (só nos casos com AiResponse válido — caminho feliz e handoff-com-reply).
 *                   Ortogonal a needsHuman: a detecção NÃO força handoff (a IA segue
 *                   respondendo), só marca a conversa.
 */
public record AiResponse(
    String reply,
    boolean needsHuman,
    String reason,
    int tokensIn,
    int tokensOut,
    long latencyMs,
    SchedulingIntent schedulingIntent,
    AiInsights insights) {

    /**
     * Construtor de conveniência sem intent nem insights — preserva a aridade histórica de
     * 6 args (synthetic outside-hours, testes). insights = AiInsights.empty() (nunca null,
     * para o OutboundService chamar hasAny() sem NPE).
     */
    public AiResponse(String reply, boolean needsHuman, String reason,
                      int tokensIn, int tokensOut, long latencyMs) {
        this(reply, needsHuman, reason, tokensIn, tokensOut, latencyMs, null, AiInsights.empty());
    }

    /**
     * Construtor de 7 args (sem insights) — preserva os call-sites da 5.15 que passam só
     * schedulingIntent. insights = empty.
     */
    public AiResponse(String reply, boolean needsHuman, String reason,
                      int tokensIn, int tokensOut, long latencyMs,
                      SchedulingIntent schedulingIntent) {
        this(reply, needsHuman, reason, tokensIn, tokensOut, latencyMs, schedulingIntent,
            AiInsights.empty());
    }
}
