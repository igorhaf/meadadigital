package com.meada.whatsapp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link AiProvider} sobre a Gemini API (generateContent), via RestClient síncrono
 * (sem Reactor/Netty — coerente com o fluxo bloqueante no @Async).
 *
 * <p>Request (shape confirmado na doc oficial):
 * <ul>
 *   <li>{@code system_instruction.parts[].text} ← Prompt.systemPrompt
 *   <li>{@code contents[]} (role user/model) ← history + userMessage
 *   <li>{@code generationConfig.responseMimeType=application/json} +
 *       {@code responseSchema} forçando {reply, needs_human, reason}
 * </ul>
 * O JSON estruturado vem como STRING em {@code candidates[0].content.parts[0].text}
 * (parseado de novo com Jackson). Tokens em {@code usageMetadata}.
 *
 * <p>Erros: HTTP 429/5xx → {@link AiTransientException} (retentável); 4xx e
 * falha de parse → {@link AiException} (fatal). Nome do modelo via GEMINI_MODEL
 * (sem default — ver RISKS.md "Nome do modelo Gemini vigente").
 */
@Component
public class GeminiProvider implements AiProvider {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiProvider(@Value("${gemini.base-url}") String baseUrl,
                          @Value("${gemini.api-key}") String apiKey,
                          @Value("${gemini.model}") String model,
                          @Value("${gemini.connect-timeout-ms:5000}") long connectTimeoutMs,
                          @Value("${gemini.read-timeout-ms:30000}") long readTimeoutMs,
                          ObjectMapper objectMapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key must be configured (env GEMINI_API_KEY)");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("gemini.model must be configured (env GEMINI_MODEL)");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        // Timeouts EXPLÍCITOS: o RestClient default não tem timeout — sem isto a
        // request poderia pendurar indefinidamente (e o cenário timeout→transient
        // nem seria alcançável). connect 5s, read 30s (IA pode ser lenta).
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs)));
        this.restClient = RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public AiResponse generate(Prompt prompt) {
        Map<String, Object> body = buildRequestBody(prompt);
        long start = System.nanoTime();
        String responseJson;
        try {
            responseJson = restClient.post()
                .uri("/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            // 429 (rate limit) e 5xx (provider indisponível) → retentável.
            if (status == 429 || e.getStatusCode().is5xxServerError()) {
                throw new AiTransientException("Gemini transient error: HTTP " + status, e);
            }
            // demais 4xx (prompt/chave inválidos) → fatal.
            throw new AiException("Gemini fatal error: HTTP " + status, e);
        } catch (Exception e) {
            // timeout / IO / conexão → retentável (transiente de rede).
            throw new AiTransientException("Gemini call failed: " + e.getMessage(), e);
        }
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        return parseResponse(responseJson, latencyMs);
    }

    /** Monta system_instruction + contents (history alternado + userMessage) + responseSchema. */
    private Map<String, Object> buildRequestBody(Prompt prompt) {
        Map<String, Object> root = new LinkedHashMap<>();

        root.put("system_instruction",
            Map.of("parts", List.of(Map.of("text", prompt.systemPrompt()))));

        List<Map<String, Object>> contents = new ArrayList<>();
        for (ConversationTurn turn : prompt.history()) {
            String role = turn.role() == ConversationTurn.Role.USER ? "user" : "model";
            contents.add(Map.of("role", role, "parts", List.of(Map.of("text", turn.text()))));
        }
        // a mensagem atual do cliente é o último turn (role user)
        contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", prompt.userMessage()))));
        root.put("contents", contents);

        root.put("generationConfig", Map.of(
            "responseMimeType", "application/json",
            "responseSchema", responseSchema()));

        return root;
    }

    /** Schema que força o JSON {reply, needs_human, reason}. */
    private Map<String, Object> responseSchema() {
        return Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                "reply", Map.of("type", "STRING"),
                "needs_human", Map.of("type", "BOOLEAN"),
                "reason", Map.of("type", "STRING")),
            "required", List.of("reply", "needs_human"));
    }

    /** Extrai o JSON estruturado de candidates[0].content.parts[0].text + tokens. */
    private AiResponse parseResponse(String responseJson, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);

            JsonNode textNode = root.path("candidates").path(0)
                .path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                throw new AiException("Gemini response missing candidates[0].content.parts[0].text");
            }
            // o texto É um JSON (responseMimeType=application/json) — parseia de novo.
            JsonNode structured = objectMapper.readTree(textNode.asText());

            String reply = structured.path("reply").asText(null);
            boolean needsHuman = structured.path("needs_human").asBoolean(false);
            String reason = structured.path("reason").asText(null);

            JsonNode usage = root.path("usageMetadata");
            int tokensIn = usage.path("promptTokenCount").asInt(0);
            int tokensOut = usage.path("candidatesTokenCount").asInt(0);

            return new AiResponse(reply, needsHuman, reason, tokensIn, tokensOut, latencyMs);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            // JSON malformado / estrutura inesperada → fatal (retry não ajuda).
            throw new AiException("Failed to parse Gemini response", e);
        }
    }
}
