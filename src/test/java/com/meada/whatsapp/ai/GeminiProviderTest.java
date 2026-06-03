package com.meada.whatsapp.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test do {@link GeminiProvider} com MockWebServer — simula a Gemini API,
 * SEM chamada real (crédito/latência/flakiness). Não precisa de Spring context
 * nem Postgres: instancia o provider direto apontando para o mock.
 *
 * <p>5 cenários: 200 resposta normal, 200 needs_human, 429→transiente,
 * 5xx→transiente, JSON inválido→fatal.
 */
class GeminiProviderTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;
    private GeminiProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        provider = new GeminiProvider(
            server.url("/").toString().replaceAll("/$", ""),   // base-url do mock
            "test-key", "test-model", 5000, 30000, MAPPER);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private final Prompt prompt = new Prompt("system", List.of(), "oi");

    /**
     * Resposta da Gemini: o texto do candidate É o JSON estruturado (string).
     * Usa ObjectMapper.writeValueAsString para escapar o JSON aninhado dentro do
     * campo "text" de forma correta (não escape manual frágil).
     */
    private String geminiBody(String innerJson, int promptTokens, int candTokens) {
        try {
            String escapedText = MAPPER.writeValueAsString(innerJson);   // vira "..." com escapes corretos
            return """
                {
                  "candidates": [{"content": {"parts": [{"text": %s}], "role": "model"}}],
                  "usageMetadata": {"promptTokenCount": %d, "candidatesTokenCount": %d}
                }
                """.formatted(escapedText, promptTokens, candTokens);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("200 normal: mapeia reply, needsHuman=false, tokens, latência")
    void normalResponse() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(geminiBody("{\"reply\":\"Ola! Como posso ajudar?\",\"needs_human\":false,\"reason\":\"\"}", 120, 18)));

        AiResponse r = provider.generate(prompt);

        assertThat(r.reply()).isEqualTo("Ola! Como posso ajudar?");
        assertThat(r.needsHuman()).isFalse();
        assertThat(r.tokensIn()).isEqualTo(120);
        assertThat(r.tokensOut()).isEqualTo(18);
        assertThat(r.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("200 needs_human=true: mapeia needsHuman e reason")
    void needsHumanResponse() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(geminiBody("{\"reply\":\"Vou te transferir.\",\"needs_human\":true,\"reason\":\"cliente pediu atendente\"}", 90, 12)));

        AiResponse r = provider.generate(prompt);

        assertThat(r.needsHuman()).isTrue();
        assertThat(r.reason()).isEqualTo("cliente pediu atendente");
        assertThat(r.reply()).isEqualTo("Vou te transferir.");   // reply preenchido mesmo com needsHuman
    }

    @Test
    @DisplayName("429: AiTransientException (retentável)")
    void rateLimited_transient() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"rate limit\"}"));

        assertThatThrownBy(() -> provider.generate(prompt))
            .isInstanceOf(AiTransientException.class);
    }

    @Test
    @DisplayName("503: AiTransientException (retentável)")
    void serverError_transient() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"));

        assertThatThrownBy(() -> provider.generate(prompt))
            .isInstanceOf(AiTransientException.class);
    }

    @Test
    @DisplayName("400: AiException fatal (não-transiente)")
    void badRequest_fatal() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid\"}"));

        assertThatThrownBy(() -> provider.generate(prompt))
            .isInstanceOf(AiException.class)
            .isNotInstanceOf(AiTransientException.class);
    }

    @Test
    @DisplayName("200 com JSON malformado no text: AiException fatal")
    void malformedInnerJson_fatal() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(geminiBody("isto nao e json", 10, 5)));

        assertThatThrownBy(() -> provider.generate(prompt))
            .isInstanceOf(AiException.class)
            .isNotInstanceOf(AiTransientException.class);
    }
}
