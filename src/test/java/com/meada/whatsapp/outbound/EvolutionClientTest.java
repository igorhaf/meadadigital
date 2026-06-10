package com.meada.whatsapp.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test do {@link EvolutionClient} com MockWebServer — simula a Evolution API,
 * sem chamada real. Não precisa de Spring context: instancia o client direto
 * apontando para o mock.
 *
 * <p>5 cenários de envio real (dryRun=false): 201 sucesso (parseia key.id),
 * 429→transient, 503→transient, 400→fatal, 201 com key.id ausente→fatal.
 * +1 cenário dry-run: HTTP suprimido, retorna id fake.
 */
class EvolutionClientTest {

    private MockWebServer server;
    private EvolutionClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new EvolutionClient(
            server.url("/").toString().replaceAll("/$", ""),   // base-url do mock
            5000, 30000, false, new ObjectMapper());           // dryRun=false: exercita o HTTP real
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /** Resposta 201 da Evolution conforme doc: key.id é o id da mensagem. */
    private String sendTextResponse(String keyId) {
        return """
            {
              "key": {"remoteJid": "5511999990000@s.whatsapp.net", "fromMe": true, "id": "%s"},
              "message": {"extendedTextMessage": {"text": "Olá!"}},
              "messageTimestamp": "1717689097",
              "status": "PENDING"
            }
            """.formatted(keyId);
    }

    @Test
    @DisplayName("201 sucesso: retorna key.id e monta a request correta (path, apikey, body)")
    void success_returnsKeyId() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setBody(sendTextResponse("BAE594145F4C59B4")));

        String id = client.sendText("inst-a", "tok-secret", "+5511999990000", "Olá, tudo bem?");

        assertThat(id).isEqualTo("BAE594145F4C59B4");

        // confirma o shape da request enviada
        RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
        assertThat(req.getPath()).isEqualTo("/message/sendText/inst-a");
        assertThat(req.getHeader("apikey")).isEqualTo("tok-secret");
        assertThat(req.getBody().readUtf8())
            .contains("\"number\":\"+5511999990000\"")
            .contains("\"text\":\"Olá, tudo bem?\"");
    }

    @Test
    @DisplayName("429: EvolutionTransientException (retentável)")
    void rateLimited_transient() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"rate limit\"}"));

        assertThatThrownBy(() -> client.sendText("inst-a", "tok", "+5511999990000", "oi"))
            .isInstanceOf(EvolutionTransientException.class);
    }

    @Test
    @DisplayName("503: EvolutionTransientException (retentável)")
    void serverError_transient() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"));

        assertThatThrownBy(() -> client.sendText("inst-a", "tok", "+5511999990000", "oi"))
            .isInstanceOf(EvolutionTransientException.class);
    }

    @Test
    @DisplayName("400: EvolutionException fatal (não-transiente)")
    void badRequest_fatal() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid\"}"));

        assertThatThrownBy(() -> client.sendText("inst-a", "tok", "+5511999990000", "oi"))
            .isInstanceOf(EvolutionException.class)
            .isNotInstanceOf(EvolutionTransientException.class);
    }

    @Test
    @DisplayName("201 com key.id ausente: EvolutionException fatal")
    void missingKeyId_fatal() {
        server.enqueue(new MockResponse()
            .setResponseCode(201)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"message\":{\"extendedTextMessage\":{\"text\":\"Olá!\"}},\"status\":\"PENDING\"}"));

        assertThatThrownBy(() -> client.sendText("inst-a", "tok", "+5511999990000", "oi"))
            .isInstanceOf(EvolutionException.class)
            .isNotInstanceOf(EvolutionTransientException.class);
    }

    @Test
    @DisplayName("dryRun=true → não faz HTTP e retorna id com prefixo 'dry-run-'")
    void dryRunSuppressesHttpAndReturnsFakeId() {
        // client dedicado com dryRun=true (o de classe é dryRun=false); aponta para o
        // mesmo mock, mas nenhuma request deve chegar nele.
        EvolutionClient dryClient = new EvolutionClient(
            server.url("/").toString().replaceAll("/$", ""),
            5000, 30000, true, new ObjectMapper());

        String id = dryClient.sendText("inst-a", "tok", "+5511999990000", "Olá, tudo bem?");

        assertThat(id).startsWith("dry-run-");
        assertThat(server.getRequestCount()).isZero();   // nenhum HTTP saiu
    }
}
