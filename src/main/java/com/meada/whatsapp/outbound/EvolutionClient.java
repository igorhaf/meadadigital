package com.meada.whatsapp.outbound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Cliente HTTP da Evolution API para envio de texto, via RestClient síncrono
 * (sem Reactor/Netty — coerente com o fluxo bloqueante no @Async).
 *
 * <p>Endpoint/shape confirmados na doc oficial v2.1.1
 * (https://doc.evolution-api.com/v2/api-reference/message-controller/send-text):
 * <ul>
 *   <li>{@code POST {baseUrl}/message/sendText/{instance}}
 *   <li>Auth: header {@code apikey} (lowercase, sem "Bearer") — o evolution_token
 *       da INSTÂNCIA, passado como parâmetro (per-instance, não @Value). O client
 *       não conhece o token; o OutboundService o injeta na chamada.
 *   <li>Body: {@code {"number": "<E.164>", "text": "<msg>"}}
 *   <li>Resposta 201: {@code key.id} é o id da mensagem enviada (→ evolution_message_id).
 * </ul>
 *
 * <p>baseUrl é GLOBAL (env, fail-fast — self-hosted, sem URL pública). Erros:
 * 429/5xx/timeout → {@link EvolutionTransientException}; 4xx (401/404) e parse →
 * {@link EvolutionException} (fatal).
 *
 * <p>Implementa {@link EvolutionSender} — o OutboundService depende da interface,
 * não desta classe (ver javadoc da interface).
 */
@Component
public class EvolutionClient implements EvolutionSender {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public EvolutionClient(@Value("${evolution.base-url}") String baseUrl,
                           @Value("${evolution.connect-timeout-ms:5000}") long connectTimeoutMs,
                           @Value("${evolution.read-timeout-ms:30000}") long readTimeoutMs,
                           ObjectMapper objectMapper) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("evolution.base-url must be configured (env EVOLUTION_BASE_URL)");
        }
        this.objectMapper = objectMapper;
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs)));
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

    /**
     * Envia uma mensagem de texto pela Evolution.
     *
     * @param instanceName nome da instância (path)
     * @param token        evolution_token da instância (header apikey) — per-instance
     * @param number       destinatário em E.164 (com +; ver RISKS se Evolution recusar)
     * @param text         conteúdo
     * @return o {@code key.id} da mensagem enviada (evolution_message_id)
     * @throws EvolutionTransientException 429/5xx/timeout — retentável
     * @throws EvolutionException          4xx/parse — fatal
     */
    @Override
    public String sendText(String instanceName, String token, String number, String text) {
        Map<String, Object> body = Map.of("number", number, "text", text);
        String responseJson;
        try {
            responseJson = restClient.post()
                .uri("/message/sendText/{instance}", instanceName)
                .header("apikey", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()   // 2xx (201 inclusive) = sucesso; 4xx/5xx lançam
                .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 429 || e.getStatusCode().is5xxServerError()) {
                throw new EvolutionTransientException("Evolution transient error: HTTP " + status, e);
            }
            throw new EvolutionException("Evolution fatal error: HTTP " + status, e);
        } catch (EvolutionException e) {
            throw e;
        } catch (Exception e) {
            // timeout / IO / conexão → retentável (transiente de rede).
            throw new EvolutionTransientException("Evolution call failed: " + e.getMessage(), e);
        }
        return extractMessageId(responseJson);
    }

    /** Extrai key.id da resposta. Ausência → fatal (parse não melhora com retry). */
    private String extractMessageId(String responseJson) {
        try {
            JsonNode idNode = objectMapper.readTree(responseJson).path("key").path("id");
            if (idNode.isMissingNode() || idNode.isNull() || idNode.asText().isBlank()) {
                throw new EvolutionException("Evolution response missing key.id");
            }
            return idNode.asText();
        } catch (EvolutionException e) {
            throw e;
        } catch (Exception e) {
            throw new EvolutionException("Failed to parse Evolution response", e);
        }
    }
}
