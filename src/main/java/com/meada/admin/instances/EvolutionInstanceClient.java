package com.meada.admin.instances;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client da API de INSTÂNCIA da Evolution (v2.3.1) — o lado de PROVISIONAMENTO, distinto do
 * {@link com.meada.outbound.EvolutionClient} (que só ENVIA mensagem).
 *
 * <p>Diferença de credencial que importa: os endpoints de instância exigem a <b>API key GLOBAL</b>
 * do servidor Evolution (env {@code AUTHENTICATION_API_KEY} de lá), não o token per-instância. O
 * token per-instância é justamente o que o {@code /instance/create} DEVOLVE (campo {@code hash}) —
 * e é ele que vai para {@code whatsapp_instances.evolution_token}.
 *
 * <p><b>Feature opcional:</b> sem {@code evolution.global-api-key} configurada o client fica
 * INDISPONÍVEL ({@link #isAvailable()} = false) e o serviço responde 503 {@code whatsapp_unavailable}.
 * Não há fail-fast: instalações que ainda não usam a conexão pelo painel sobem normalmente.
 *
 * <p><b>GUARD DO INCIDENTE 2026-06-10 (RISKS.md):</b> {@link #applySafetySettings} força
 * {@code syncFullHistory=false} na instância. Foi o re-sync do histórico do Baileys que disparou
 * respostas automáticas a contatos reais. Toda instância criada por aqui nasce com o guard ligado.
 *
 * <p>Formas de resposta verificadas contra a Evolution v2.3.1 REAL (sonda de 2026-07-11), não
 * contra documentação.
 */
@Component
public class EvolutionInstanceClient implements EvolutionInstanceApi {

    private static final Logger log = LoggerFactory.getLogger(EvolutionInstanceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String globalApiKey;

    public EvolutionInstanceClient(@Value("${evolution.base-url}") String baseUrl,
                                   @Value("${evolution.global-api-key:}") String globalApiKey,
                                   @Value("${evolution.connect-timeout-ms:5000}") long connectTimeoutMs,
                                   @Value("${evolution.instance-read-timeout-ms:60000}") long readTimeoutMs,
                                   ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.globalApiKey = globalApiKey == null ? "" : globalApiKey.trim();
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs)));
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl == null ? "" : baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

    /** false quando a API key global não foi configurada → conexão pelo painel indisponível (503). */
    @Override
    public boolean isAvailable() {
        return !globalApiKey.isBlank();
    }

    /**
     * Cria a instância na Evolution e devolve o token + o QR inicial.
     *
     * @throws EvolutionInstanceException 400 (nome duplicado) e demais erros da Evolution
     */
    @Override
    public CreatedInstance createInstance(String instanceName) {
        Map<String, Object> body = Map.of(
            "instanceName", instanceName,
            "qrcode", true,
            "integration", "WHATSAPP-BAILEYS");
        JsonNode root = post("/instance/create", body, instanceName);
        String token = root.path("hash").asText(null);
        String qr = root.path("qrcode").path("base64").asText(null);
        if (token == null || token.isBlank()) {
            throw new EvolutionInstanceException("Evolution não devolveu o token (hash) da instância");
        }
        return new CreatedInstance(instanceName, token, qr);
    }

    /**
     * Re-obtém o QR de uma instância que está aguardando pareamento
     * ({@code GET /instance/connect}). O QR expira e é rotacionado pela Evolution.
     */
    @Override
    public Optional<String> fetchQrCode(String instanceName) {
        try {
            String json = restClient.get()
                .uri("/instance/connect/{instance}", instanceName)
                .header("apikey", globalApiKey)
                .retrieve()
                .body(String.class);
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            String base64 = root.path("base64").asText(null);
            return (base64 == null || base64.isBlank()) ? Optional.empty() : Optional.of(base64);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new EvolutionInstanceException("Evolution connect falhou: HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new EvolutionInstanceException("Evolution connect falhou: " + e.getMessage(), e);
        }
    }

    /**
     * Estado atual da instância. Usa {@code fetchInstances} (e não {@code connectionState}) porque
     * é a ÚNICA rota que devolve o {@code ownerJid} — a FONTE DA VERDADE do número conectado.
     *
     * @return empty se a instância não existe mais na Evolution (404 / lista vazia)
     */
    @Override
    public Optional<InstanceState> fetchState(String instanceName) {
        try {
            String json = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/instance/fetchInstances")
                    .queryParam("instanceName", instanceName).build())
                .header("apikey", globalApiKey)
                .retrieve()
                .body(String.class);
            JsonNode root = objectMapper.readTree(json == null ? "[]" : json);
            JsonNode node = root.isArray() ? (root.isEmpty() ? null : root.get(0)) : root;
            if (node == null || node.isNull()) {
                return Optional.empty();
            }
            return Optional.of(new InstanceState(
                node.path("connectionStatus").asText(null),
                node.path("ownerJid").asText(null),
                node.path("profileName").asText(null)));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new EvolutionInstanceException("Evolution fetchInstances falhou: HTTP "
                + e.getStatusCode().value(), e);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new EvolutionInstanceException("Evolution fetchInstances falhou: " + e.getMessage(), e);
        }
    }

    /**
     * GUARD DO INCIDENTE (RISKS.md, 2026-06-10): desliga o sync do histórico completo do Baileys.
     * Sem isso, ao parear, a Evolution re-emite mensagens ANTIGAS como se fossem novas e a IA
     * responde automaticamente a contatos reais. Também ignora grupos (o produto é 1:1).
     */
    @Override
    public void applySafetySettings(String instanceName) {
        Map<String, Object> body = Map.of(
            "syncFullHistory", false,      // ← o guard
            "groupsIgnore", true,
            "rejectCall", false,
            "alwaysOnline", false,
            "readMessages", false,
            "readStatus", false);
        post("/settings/set/" + instanceName, body, instanceName);
    }

    /**
     * Aponta o webhook da instância para o Meada. O secret viaja no HEADER {@code apikey}
     * (a Evolution suporta headers customizados no webhook) — e não no query param, que
     * vazaria em access log (risco Aceito no RISKS.md; aqui ele é evitado).
     */
    @Override
    public void setWebhook(String instanceName, String webhookUrl, String webhookSecret) {
        Map<String, Object> webhook = Map.of(
            "enabled", true,
            "url", webhookUrl,
            "headers", Map.of("apikey", webhookSecret),
            "byEvents", false,
            "base64", false,
            "events", List.of("MESSAGES_UPSERT"));
        post("/webhook/set/" + instanceName, Map.of("webhook", webhook), instanceName);
    }

    /** Desconecta o número (encerra a sessão do WhatsApp) — a instância CONTINUA existindo. */
    @Override
    public void logout(String instanceName) {
        try {
            restClient.delete()
                .uri("/instance/logout/{instance}", instanceName)
                .header("apikey", globalApiKey)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            // 404 (instância já sumiu) é benigno no logout — o alvo já está no estado desejado.
            if (e.getStatusCode().value() != 404) {
                throw new EvolutionInstanceException("Evolution logout falhou: HTTP "
                    + e.getStatusCode().value(), e);
            }
            log.info("evolution logout: instância {} já não existe (404) — tratado como desconectada", instanceName);
        } catch (RestClientException e) {
            throw new EvolutionInstanceException("Evolution logout falhou: " + e.getMessage(), e);
        }
    }

    private JsonNode post(String path, Object body, String instanceName) {
        try {
            String json = restClient.post()
                .uri(path)
                .header("apikey", globalApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("evolution instance API falhou path={} instance={} status={}", path, instanceName, status);
            throw new EvolutionInstanceException("Evolution " + path + " falhou: HTTP " + status, e, status);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new EvolutionInstanceException("Evolution " + path + " falhou: " + e.getMessage(), e);
        }
    }
}
