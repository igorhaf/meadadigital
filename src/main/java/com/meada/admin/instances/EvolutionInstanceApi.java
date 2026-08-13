package com.meada.admin.instances;

import java.util.Optional;

/**
 * Porta para a API de INSTÂNCIA da Evolution (provisionamento), separada da porta de ENVIO
 * ({@link com.meada.outbound.EvolutionSender}). Mesmo padrão da casa: interface aqui, implementação
 * HTTP no {@link EvolutionInstanceClient}, fake determinístico nos testes — nenhum teste depende de
 * um container Evolution de verdade.
 */
public interface EvolutionInstanceApi {

    /** Estados que a Evolution devolve em {@code connectionStatus}/{@code state}. */
    String STATE_OPEN = "open";
    String STATE_CONNECTING = "connecting";

    /** Instância recém-criada: o token per-instância (campo {@code hash}) + o QR inicial (data-URI). */
    record CreatedInstance(String instanceName, String token, String qrCodeBase64) {}

    /** Estado vivo na Evolution. {@code ownerJid} (o número) só existe depois do pareamento. */
    record InstanceState(String state, String ownerJid, String profileName) {}

    /** false quando o servidor não tem a API key global → conexão pelo painel desligada (503). */
    boolean isAvailable();

    CreatedInstance createInstance(String instanceName);

    /** Re-obtém o QR (a Evolution rotaciona o código). Empty se a instância não existe. */
    Optional<String> fetchQrCode(String instanceName);

    /** Estado + número conectado. Empty se a instância não existe mais na Evolution. */
    Optional<InstanceState> fetchState(String instanceName);

    /** GUARD do incidente 2026-06-10: {@code syncFullHistory=false} + ignora grupos. */
    void applySafetySettings(String instanceName);

    /** Aponta o webhook da instância para o Meada (secret no header {@code apikey}). */
    void setWebhook(String instanceName, String webhookUrl, String webhookSecret);

    /** Encerra a sessão do WhatsApp. A instância CONTINUA existindo. */
    void logout(String instanceName);
}
