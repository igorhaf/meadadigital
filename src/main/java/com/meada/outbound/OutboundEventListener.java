package com.meada.outbound;

import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Ponte entre a publicação do {@link MessageInboundProcessedEvent} (no WebhookService,
 * dentro da transação inbound) e o {@link OutboundService} (resposta da IA + envio).
 *
 * <p>Bean SEPARADO do OutboundService de propósito: o método assíncrono precisa ser
 * invocado VIA PROXY (não self-invocation), e manter o {@code process} como método
 * normal síncrono preserva os testes da Fase 3.3 que o chamam direto.
 *
 * <p>Duas anotações no mesmo método:
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} — só dispara DEPOIS que a
 *       transação que persistiu a inbound commitou. Garante que o process relê um
 *       estado durável (handled_by, phone, histórico).
 *   <li>{@code @Async("outboundExecutor")} — roda no pool dedicado (ver AsyncConfig),
 *       fora da thread do webhook, para a resposta HTTP ao Evolution não esperar a IA.
 * </ul>
 */
@Component
public class OutboundEventListener {

    private final OutboundService outboundService;

    public OutboundEventListener(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    /**
     * Recebe o evento após o commit, numa thread do pool, e dispara o processamento.
     *
     * <p>MDC: popula {@code conversation_id} e {@code company_id} para correlacionar os
     * logs do processamento async com os do webhook. O {@code finally} com
     * {@link MDC#clear()} é OBRIGATÓRIO — a thread do pool é REUSADA; sem o clear o
     * próximo evento herdaria o MDC sujo do anterior.
     *
     * <p>Não captura exceções: o {@code process} trata toda a matriz internamente
     * (retorna outcome, nunca lança). O inesperado (ex. PromptBuilder lançando por
     * config ausente) sobe para o AsyncUncaughtExceptionHandler do AsyncConfig.
     */
    @Async("outboundExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageInboundProcessed(MessageInboundProcessedEvent event) {
        MDC.put("conversation_id", event.conversationId().toString());
        MDC.put("company_id", event.companyId().toString());
        try {
            outboundService.process(event);
        } finally {
            MDC.clear();
        }
    }
}
