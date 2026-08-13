package com.meada.ai;

import java.time.Instant;

/**
 * Intenção de agendamento detectada pela IA numa mensagem do cliente (camada 5.15 #29).
 *
 * <p>Preenchido pelo {@link GeminiProvider} quando o modelo, ao responder, detecta que o
 * cliente quer marcar/agendar/reservar um serviço — e SÓ nesse caso (a maioria das
 * mensagens não gera intent; o {@code schedulingIntent} do {@link AiResponse} fica null).
 * A detecção NÃO interrompe o atendimento: a IA segue respondendo texto normal; isto só
 * MARCA a conversa (o OutboundService persiste em {@code conversations.scheduling_intent}),
 * e o tenant decide quando intervir.
 *
 * <p>{@code detectedAt} é FATO DO SERVIDOR — preenchido com {@code Instant.now()} no
 * parsing, NÃO vem do modelo (timestamp não é decisão da IA). Os outros quatro campos
 * vêm do modelo via o sub-objeto {@code scheduling_intent} do JSON estruturado.
 *
 * @param detectedAt  quando o backend parseou a detecção (Instant.now() no provider).
 * @param serviceHint serviço que o cliente mencionou querer marcar; NULLABLE (string
 *                    livre, não relação com a tabela services).
 * @param whenHint    quando ele mencionou, em linguagem natural ("amanhã 14h", "sábado de
 *                    manhã"); NULLABLE.
 * @param urgency     urgência expressa: "low" | "normal" | "high" (enum no schema do
 *                    modelo; string aqui — o provider não valida além do que o schema força).
 * @param rawExcerpt  trecho exato da mensagem do cliente que disparou a detecção (o modelo
 *                    é instruído a limitar ~200 chars). Não-null (required no schema).
 */
public record SchedulingIntent(
    Instant detectedAt,
    String serviceHint,
    String whenHint,
    String urgency,
    String rawExcerpt) {
}
