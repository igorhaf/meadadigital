package com.meada.ai;

/**
 * Abstração do provedor de IA. Uma operação: dado um {@link Prompt} montado,
 * gera a {@link AiResponse} estruturada.
 *
 * <p>Abstrai o provider concreto (GeminiProvider no MVP) — trocar Gemini↔OpenAI é
 * fornecer outra implementação, sem tocar o OutboundService. Sem hierarquia
 * pré-fabricada (sem AbstractAiProvider): refatora quando o 2º provider aparecer.
 *
 * @throws AiTransientException falha retentável (429/5xx/timeout) — caller retenta
 * @throws AiException          falha fatal (4xx/parse) — caller faz flip humano
 */
public interface AiProvider {

    AiResponse generate(Prompt prompt);
}
