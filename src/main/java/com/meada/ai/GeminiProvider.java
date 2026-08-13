package com.meada.ai;

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
import java.time.Instant;
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
    private final com.meada.admin.health.ErrorLogger errorLogger;
    private final String apiKey;
    private final String model;

    public GeminiProvider(@Value("${gemini.base-url}") String baseUrl,
                          @Value("${gemini.api-key}") String apiKey,
                          @Value("${gemini.model}") String model,
                          @Value("${gemini.connect-timeout-ms:5000}") long connectTimeoutMs,
                          @Value("${gemini.read-timeout-ms:30000}") long readTimeoutMs,
                          ObjectMapper objectMapper,
                          com.meada.admin.health.ErrorLogger errorLogger) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api-key must be configured (env GEMINI_API_KEY)");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("gemini.model must be configured (env GEMINI_MODEL)");
        }
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.errorLogger = errorLogger;
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
            // demais 4xx (prompt/chave inválidos) → fatal. Erro alertável (camada 6.4): registra no
            // error_log para a tela de erros do super-admin. Best-effort (ErrorLogger nunca lança).
            AiException fatal = new AiException("Gemini fatal error: HTTP " + status, e);
            errorLogger.log("GeminiProvider", fatal, java.util.Map.of("httpStatus", status, "model", model));
            throw fatal;
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
            "properties", new java.util.LinkedHashMap<>(Map.of(
                "reply", Map.of("type", "STRING"),
                "needs_human", Map.of("type", "BOOLEAN"),
                "reason", Map.of("type", "STRING"),
                "scheduling_intent", schedulingIntentSchema(),
                "cancellation_intent", detectedIntentSchema(),
                "complaint_intent", detectedIntentSchema(),
                "extracted_data", Map.of("type", "OBJECT", "nullable", true),
                "memory_update", Map.of("type", "OBJECT", "nullable", true),
                "detected_tone", Map.of("type", "STRING",
                    "enum", List.of("formal", "informal", "neutro", "irritado")),
                "appointment_action", appointmentActionSchema())),
            "required", List.of("reply", "needs_human"));
    }

    /**
     * Sub-objeto OPCIONAL de ação de agendamento (camada 5.19 #60/#64). kind enum
     * book|reschedule|cancel; when_iso (ISO local) só p/ book/reschedule; service_hint livre.
     */
    private Map<String, Object> appointmentActionSchema() {
        return Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                "kind", Map.of("type", "STRING", "enum", List.of("book", "reschedule", "cancel")),
                "when_iso", Map.of("type", "STRING", "nullable", true),
                "service_hint", Map.of("type", "STRING", "nullable", true)),
            "required", List.of("kind"));
    }

    /**
     * Sub-objeto OPCIONAL de intent genérico (cancelamento #51, reclamação #52) — camada
     * 5.18. summary + raw_excerpt; detected_at é fato do servidor (não no schema).
     */
    private Map<String, Object> detectedIntentSchema() {
        return Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                "summary", Map.of("type", "STRING"),
                "raw_excerpt", Map.of("type", "STRING")),
            "required", List.of("summary", "raw_excerpt"));
    }

    /**
     * Sub-objeto OPCIONAL scheduling_intent (camada 5.15 #29) — NÃO entra em required do
     * schema-pai: o modelo só o preenche ao detectar intenção de agendar (a maioria das
     * respostas o omite). detected_at NÃO está aqui: é fato do servidor (Instant.now() no
     * parse), não decisão do modelo. urgency é enum; service_hint/when_hint nullable.
     *
     * <p>Opção A (decisão cravada): a detecção viaja como sub-objeto do JSON estruturado,
     * NÃO como function calling — a Gemini API proíbe combinar responseSchema (JSON mode)
     * com tools na mesma request (HTTP 400). Mesmo efeito de produto, contrato
     * reply/needs_human intacto.
     */
    private Map<String, Object> schedulingIntentSchema() {
        return Map.of(
            "type", "OBJECT",
            "properties", Map.of(
                "service_hint", Map.of("type", "STRING", "nullable", true),
                "when_hint", Map.of("type", "STRING", "nullable", true),
                "urgency", Map.of("type", "STRING", "enum", List.of("low", "normal", "high")),
                "raw_excerpt", Map.of("type", "STRING")),
            "required", List.of("urgency", "raw_excerpt"));
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

            SchedulingIntent schedulingIntent = parseSchedulingIntent(structured);
            AiInsights insights = parseInsights(structured);

            return new AiResponse(reply, needsHuman, reason, tokensIn, tokensOut, latencyMs,
                schedulingIntent, insights);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            // JSON malformado / estrutura inesperada → fatal (retry não ajuda).
            throw new AiException("Failed to parse Gemini response", e);
        }
    }

    /**
     * Extrai o sub-objeto OPCIONAL scheduling_intent do JSON estruturado (camada 5.15 #29).
     * Retorna null quando ausente/null (caso da maioria das mensagens). detected_at é
     * preenchido AQUI com Instant.now() — fato do servidor, não vem do modelo. Os demais
     * campos vêm do modelo; service_hint/when_hint podem faltar (asText(null) → null);
     * urgency e raw_excerpt são required no schema (presença garantida quando o objeto vem).
     */
    private SchedulingIntent parseSchedulingIntent(JsonNode structured) {
        JsonNode intent = structured.path("scheduling_intent");
        if (intent.isMissingNode() || intent.isNull()) {
            return null;
        }
        String serviceHint = intent.path("service_hint").asText(null);
        String whenHint = intent.path("when_hint").asText(null);
        String urgency = intent.path("urgency").asText(null);
        String rawExcerpt = intent.path("raw_excerpt").asText(null);
        return new SchedulingIntent(Instant.now(), serviceHint, whenHint, urgency, rawExcerpt);
    }

    /**
     * Extrai os sub-objetos OPCIONAIS da camada 5.18 (cancelamento #51, reclamação #52,
     * extracted_data #53, memory_update #55, detected_tone #58). Cada um null quando
     * ausente. Retorna AiInsights.empty() se nada veio (nunca null).
     */
    private AiInsights parseInsights(JsonNode structured) {
        DetectedIntent cancellation = parseDetectedIntent(structured.path("cancellation_intent"));
        DetectedIntent complaint = parseDetectedIntent(structured.path("complaint_intent"));
        JsonNode extracted = optionalObject(structured.path("extracted_data"));
        JsonNode memory = optionalObject(structured.path("memory_update"));
        String tone = structured.path("detected_tone").asText(null);
        if (tone != null && tone.isBlank()) {
            tone = null;
        }
        AppointmentAction appointment = parseAppointmentAction(structured.path("appointment_action"));
        return new AiInsights(cancellation, complaint, extracted, memory, tone, appointment);
    }

    /** Sub-objeto appointment_action (camada 5.19), ou null se ausente/sem kind. */
    private AppointmentAction parseAppointmentAction(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String kind = node.path("kind").asText(null);
        if (kind == null || kind.isBlank()) {
            return null;
        }
        return new AppointmentAction(
            kind, node.path("when_iso").asText(null), node.path("service_hint").asText(null));
    }

    /** Sub-objeto de DetectedIntent (cancelamento/reclamação), ou null se ausente. */
    private DetectedIntent parseDetectedIntent(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        return new DetectedIntent(
            Instant.now(), node.path("summary").asText(null), node.path("raw_excerpt").asText(null));
    }

    /** Retorna o JsonNode se for um objeto não-vazio; null caso contrário. */
    private JsonNode optionalObject(JsonNode node) {
        if (node.isMissingNode() || node.isNull() || !node.isObject() || node.isEmpty()) {
            return null;
        }
        return node;
    }
}
