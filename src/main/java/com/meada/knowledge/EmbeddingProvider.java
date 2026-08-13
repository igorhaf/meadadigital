package com.meada.knowledge;

import java.util.List;

/**
 * Gera embeddings para uma lista de textos. Abstrai o provider concreto
 * ({@link LocalEmbeddingProvider} sobre o sidecar Python no MVP) — trocar por uma API
 * externa de embedding depois é só uma nova implementação, sem tocar
 * ingestão/retrieval. Os testes injetam um fake determinístico.
 */
public interface EmbeddingProvider {

    /**
     * @param texts textos a embedar (não vazio).
     * @param kind  PASSAGE (trechos indexados) ou QUERY (consulta do usuário) — o E5
     *              exige o prefixo correspondente.
     * @return um vetor por texto, na MESMA ordem; cada vetor com {@code dim} dimensões
     *         (384 para o multilingual-e5-small). Normalizados (cosine == dot product).
     */
    List<float[]> embed(List<String> texts, EmbeddingKind kind);
}
