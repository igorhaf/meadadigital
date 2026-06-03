package com.meada.whatsapp.ai;

/**
 * Falha TRANSIENTE ao chamar a IA — vale retentar: HTTP 429 (rate limit), 5xx
 * (provider indisponível) ou timeout. O OutboundService (Fase 3.3) retenta com
 * backoff; se esgotar as tentativas, aí sim faz flip handled_by='human'.
 *
 * <p>Subtipo de {@link AiException} para que um {@code catch (AiException)} pegue
 * ambas, mas o retry possa distinguir a transiente da fatal por tipo.
 */
public class AiTransientException extends AiException {

    public AiTransientException(String message) {
        super(message);
    }

    public AiTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
