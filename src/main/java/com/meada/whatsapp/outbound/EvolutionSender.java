package com.meada.whatsapp.outbound;

/**
 * Contrato de envio outbound pela Evolution API. Abstrai o {@link EvolutionClient}
 * (implementação HTTP real) para que o OutboundService dependa da interface, não da
 * classe concreta — simétrico ao {@code AiProvider} do pacote ai, e permite um fake
 * controlável nos testes sem estender a classe real (que constrói um RestClient).
 *
 * <p>As exceções são unchecked (não declaradas no {@code throws}), mas documentadas
 * via {@code @throws} para o caller saber o que pegar — mesmo padrão do AiProvider:
 * transiente é retentável (o RetryRunner reexecuta), fatal não.
 */
public interface EvolutionSender {

    /**
     * Envia uma mensagem de texto pela Evolution.
     *
     * @param instanceName nome da instância (path da URL)
     * @param token        evolution_token da instância (header apikey) — per-instance
     * @param number       destinatário em E.164
     * @param text         conteúdo
     * @return o {@code key.id} da mensagem enviada (evolution_message_id)
     * @throws EvolutionTransientException 429/5xx/timeout — retentável
     * @throws EvolutionException          4xx/parse — fatal
     */
    String sendText(String instanceName, String token, String number, String text);
}
