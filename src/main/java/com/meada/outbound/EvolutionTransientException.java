package com.meada.outbound;

/**
 * Falha TRANSIENTE ao enviar pela Evolution — vale retentar: HTTP 429 (rate
 * limit), 5xx (Evolution indisponível) ou timeout/IO. O OutboundService retenta
 * com backoff; se esgotar, faz flip handled_by='human' (matriz caso 7).
 *
 * <p>Subtipo de {@link EvolutionException} para que um {@code catch (EvolutionException)}
 * pegue ambas, mas o retry distinga a transiente da fatal por tipo. Espelha
 * {@code com.meada.ai.AiTransientException}.
 */
public class EvolutionTransientException extends EvolutionException {

    public EvolutionTransientException(String message) {
        super(message);
    }

    public EvolutionTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
