package com.meada.admin.instances;

/**
 * Estado da conexão do WhatsApp do tenant, como o painel vê.
 *
 * @param available    false quando o servidor não tem {@code evolution.global-api-key} — a conexão
 *                     pelo painel está desligada (a tela mostra o aviso em vez dos botões)
 * @param status       {@code not_configured} (nunca conectou) · {@code connecting} (aguardando o QR
 *                     ser escaneado) · {@code connected} · {@code disconnected}
 * @param phoneNumber  o número CONECTADO em E.164 — vem do {@code ownerJid} da Evolution, NUNCA de
 *                     um input do tenant. null enquanto não houver pareamento.
 * @param profileName  nome do perfil do WhatsApp conectado (só informativo)
 * @param instanceName nome da instância na Evolution (derivado do slug da empresa)
 */
public record WhatsappConnection(
    boolean available,
    String status,
    String phoneNumber,
    String profileName,
    String instanceName) {

    static final String NOT_CONFIGURED = "not_configured";
    static final String CONNECTING = "connecting";
    static final String CONNECTED = "connected";
    static final String DISCONNECTED = "disconnected";

    static WhatsappConnection unavailable() {
        return new WhatsappConnection(false, NOT_CONFIGURED, null, null, null);
    }

    static WhatsappConnection notConfigured() {
        return new WhatsappConnection(true, NOT_CONFIGURED, null, null, null);
    }
}
