package com.meada.whatsapp.webhook;

import org.slf4j.event.Level;

/**
 * Desfecho do processamento de um webhook da Evolution. Espinha do fluxo do
 * {@link WebhookService}: cada ramo de decisão retorna um destes.
 *
 * <p>Todos mapeiam para HTTP 200 no controller (a distinção vive no log, não no
 * status — payload estruturalmente inválido nem chega ao service: é barrado pelo
 * @Valid antes, virando 400). Cada outcome carrega seu {@link Level} de log:
 * INFO para desfechos benignos/esperados, WARN para os que merecem atenção
 * (instância não provisionada, JID que não sabemos tratar).
 */
public enum WebhookOutcome {

    /** Mensagem inbound de texto persistida com sucesso. */
    PROCESSED(Level.INFO),

    /** Evento != "messages.upsert" (presence, connection, etc.) — tráfego legítimo, ignorado. */
    IGNORED_NON_MESSAGE_EVENT(Level.INFO),

    /** fromMe true (ou null, tratado defensivamente como nossa) — eco de mensagem nossa. */
    IGNORED_FROM_ME(Level.INFO),

    /** instance_name desconhecido — instância não provisionada no nosso banco. */
    IGNORED_UNKNOWN_INSTANCE(Level.WARN),

    /** Mensagem de grupo (@g.us) — fora do escopo (atendimento 1:1). */
    IGNORED_GROUP(Level.INFO),

    /** Lista de transmissão/status (@broadcast). */
    IGNORED_BROADCAST(Level.INFO),

    /** JID não-suportado (@lid, malformado, etc.) — merece atenção (rastrear o rawJid). */
    IGNORED_UNKNOWN_JID(Level.WARN),

    /** Mensagem sem texto (mídia, sticker, reaction, ou data.message null). */
    IGNORED_NON_TEXT(Level.INFO),

    /** evolution_message_id já existia — reentrega de webhook, não reprocessa. */
    IGNORED_DUPLICATE(Level.INFO),

    /** messageTimestamp mais velho que webhook.message-max-age-seconds (default 180s).
     *  Tipicamente append-on-reconnect: a Evolution reentrega mensagens antigas como
     *  messages.upsert ao reconectar a instância. WARN — distinto dos IGNORED_* INFO:
     *  stale chegando = "algo reconectou", merece visibilidade no log. */
    IGNORED_STALE(Level.WARN);

    private final Level logLevel;

    WebhookOutcome(Level logLevel) {
        this.logLevel = logLevel;
    }

    public Level logLevel() {
        return logLevel;
    }
}
