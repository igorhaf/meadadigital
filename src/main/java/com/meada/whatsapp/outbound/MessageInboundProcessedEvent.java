package com.meada.whatsapp.outbound;

import java.util.UUID;

/**
 * Evento disparado quando uma mensagem inbound foi persistida com sucesso (outcome
 * PROCESSED do WebhookService) — trigger do pipeline de resposta da IA.
 *
 * <p>Carrega o {@code userMessage} (IDENTIDADE do disparo: o texto exato que
 * iniciou ESTE processamento — vai no evento porque pode mudar entre o publish e o
 * processamento async, ex. duas mensagens rápidas) + os IDs do contexto. O phone
 * do contato, o token da instância e o histórico são PROJEÇÃO ESTÁVEL — lidos do
 * banco no processamento (não mudam entre publish e processamento, reler é seguro)
 * e por isso NÃO vão no evento (mantém o payload mínimo e sem PII).
 *
 * <p>Na Fase 3.3 o OutboundService recebe este record direto (síncrono nos testes).
 * Na Fase 3.4 o WebhookService o publica e um @TransactionalEventListener(AFTER_COMMIT)
 * + @Async dispara o processamento.
 *
 * @param companyId          tenant
 * @param conversationId     conversa (resolve handled_by, phone, histórico)
 * @param whatsappInstanceId instância (resolve o evolution_token para o envio)
 * @param userMessage        texto da inbound que disparou este processamento
 */
public record MessageInboundProcessedEvent(
    UUID companyId,
    UUID conversationId,
    UUID whatsappInstanceId,
    String userMessage) {
}
