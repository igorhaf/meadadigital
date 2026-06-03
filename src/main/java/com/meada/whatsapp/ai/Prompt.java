package com.meada.whatsapp.ai;

import java.util.List;

/**
 * Prompt pronto para enviar a um {@link AiProvider} — montado pelo PromptBuilder,
 * agnóstico ao provider concreto (o GeminiProvider/OpenAiProvider mapeia para o
 * formato da sua API).
 *
 * @param systemPrompt instrução de sistema (papel + dados do tenant: tone, regras,
 *                     restrições, services, faqs, horários). Não-null.
 * @param history      turns anteriores da conversa, em ordem cronológica. Não-null;
 *                     pode ser LISTA VAZIA (conversa nova, sem histórico).
 * @param userMessage  a mensagem inbound atual que dispara a resposta. Não-null.
 */
public record Prompt(String systemPrompt, List<ConversationTurn> history, String userMessage) {
}
