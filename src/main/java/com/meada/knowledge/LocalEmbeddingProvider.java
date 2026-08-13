package com.meada.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link EmbeddingProvider} sobre o sidecar Python (FastAPI + sentence-transformers),
 * via RestClient síncrono — mesmo padrão do {@code GeminiProvider} (sem Reactor/Netty,
 * coerente com o fluxo bloqueante da ingestão).
 *
 * <p>POST /embed {"texts": [...], "kind": "passage"|"query"} → {"vectors", "model", "dim"}.
 * O sidecar aceita até 32 textos por request; lotes maiores são quebrados aqui em batches
 * de 32 e concatenados, preservando a ordem.
 *
 * <p>Endpoint e timeouts via config (knowledge.*). read-timeout largo (60s) porque um
 * batch de embeddings em CPU pode demorar. Valida em runtime que dim == 384 (o resto do
 * RAG assume vector(384)); divergência é erro fatal de configuração.
 */
@Component
public class LocalEmbeddingProvider implements EmbeddingProvider {

    private static final int EXPECTED_DIM = 384;
    private static final int MAX_BATCH = 32;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public LocalEmbeddingProvider(
            @Value("${knowledge.embedding-endpoint}") String endpoint,
            @Value("${knowledge.embedding-connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${knowledge.embedding-read-timeout-ms:60000}") long readTimeoutMs,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(
            ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .withReadTimeout(Duration.ofMillis(readTimeoutMs)));
        this.restClient = RestClient.builder()
            .baseUrl(endpoint)
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public List<float[]> embed(List<String> texts, EmbeddingKind kind) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<float[]> all = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_BATCH) {
            List<String> batch = texts.subList(start, Math.min(start + MAX_BATCH, texts.size()));
            all.addAll(embedBatch(batch, kind));
        }
        return all;
    }

    private List<float[]> embedBatch(List<String> batch, EmbeddingKind kind) {
        String responseJson;
        try {
            responseJson = restClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("texts", batch, "kind", kind.value()))
                .retrieve()
                .body(String.class);
        } catch (Exception e) {
            throw new EmbeddingException("embedding sidecar call failed: " + e.getMessage(), e);
        }
        return parseVectors(responseJson, batch.size());
    }

    private List<float[]> parseVectors(String responseJson, int expectedCount) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            int dim = root.path("dim").asInt(-1);
            if (dim != EXPECTED_DIM) {
                throw new EmbeddingException(
                    "embedding dim mismatch: expected " + EXPECTED_DIM + " got " + dim);
            }
            JsonNode vectors = root.path("vectors");
            if (!vectors.isArray() || vectors.size() != expectedCount) {
                throw new EmbeddingException("embedding vectors count mismatch: expected "
                    + expectedCount + " got " + (vectors.isArray() ? vectors.size() : "n/a"));
            }
            List<float[]> result = new ArrayList<>(expectedCount);
            for (JsonNode vec : vectors) {
                float[] arr = new float[EXPECTED_DIM];
                for (int i = 0; i < EXPECTED_DIM; i++) {
                    arr[i] = (float) vec.path(i).asDouble();
                }
                result.add(arr);
            }
            return result;
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("failed to parse embedding response", e);
        }
    }
}
