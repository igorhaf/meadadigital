package com.meada.whatsapp.outbound;

import org.slf4j.event.Level;

/**
 * Desfecho do processamento outbound (OutboundService). Espelha o
 * {@code WebhookOutcome} da camada 2: cada ramo da matriz de fluxo retorna um
 * destes, e cada um carrega seu {@link Level} de log.
 *
 * <p>Mapeamento para a matriz (Parte A da Fase 3.3):
 * <ul>
 *   <li>{@code PROCESSED} — caso 6: ¬needsHuman + reply, enviado e gravado. INFO.
 *   <li>{@code FLIPPED_AI_HANDOFF} — casos 1+2: a IA pediu humano (needsHuman=true),
 *       com ou sem reply. Conversa vira 'human'. INFO.
 *   <li>{@code FLIPPED_AI_BAD_REPLY} — caso 3: ¬needsHuman mas reply vazio
 *       (contrato quebrado). Flip + investigar prompt/modelo. WARN.
 *   <li>{@code FLIPPED_AI_EXHAUSTED} — casos 4+5: a IA falhou (transient esgotado
 *       OU fatal). Flip. WARN.
 *   <li>{@code FLIPPED_EVOLUTION_EXHAUSTED} — caso 7: envio falhou por transient
 *       após retries. Flip. WARN.
 *   <li>{@code EVOLUTION_CONFIG_ERROR} — casos 8+9: envio fatal (401/404) ou
 *       phone/token ausente — canal inutilizável. SEM flip (humano usaria o mesmo
 *       canal quebrado; flip empilharia backlog invisível). ERROR alertável.
 *   <li>{@code SKIPPED_NOT_AI} — pré-condição: a conversa já é 'human' (humano
 *       assumiu) ou sumiu. Não processa. INFO.
 * </ul>
 */
public enum OutboundOutcome {

    PROCESSED(Level.INFO),
    FLIPPED_AI_HANDOFF(Level.INFO),
    FLIPPED_AI_BAD_REPLY(Level.WARN),
    FLIPPED_AI_EXHAUSTED(Level.WARN),
    FLIPPED_EVOLUTION_EXHAUSTED(Level.WARN),
    EVOLUTION_CONFIG_ERROR(Level.ERROR),
    SKIPPED_NOT_AI(Level.INFO);

    private final Level logLevel;

    OutboundOutcome(Level logLevel) {
        this.logLevel = logLevel;
    }

    public Level logLevel() {
        return logLevel;
    }
}
