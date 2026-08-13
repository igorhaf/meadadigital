package com.meada.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Retrieval semântico (camada 5.13.c/.d): dado a mensagem do cliente, embeda a consulta
 * (kind=QUERY) e busca os top-N chunks mais similares do tenant acima do threshold.
 *
 * <p>Query direta via JdbcTemplate (o backend é service_role) em vez da RPC
 * search_knowledge_chunks — a RPC existe para o tenant consumir via PostgREST; aqui o
 * backend passa company_id explícito e filtra documentos active/não-deletados. cosine
 * distance (<=>) com embeddings normalizados; similarity = 1 - distância.
 */
@Service
public class KnowledgeRetrievalService {

    private static final double MATCH_THRESHOLD = 0.65;
    private static final int MATCH_COUNT = 5;

    // o ?::vector aparece 3x (similarity no SELECT, no WHERE e no ORDER BY) — todos
    // recebem o MESMO literal do embedding da query.
    private static final String SEARCH =
        "select d.title, c.chunk_index, c.content, "
        + "       (1 - (c.embedding <=> ?::vector)) as similarity "
        + "from knowledge_chunks c "
        + "join knowledge_documents d on d.id = c.document_id "
        + "where c.company_id = ? and d.active and d.deleted_at is null "
        + "  and (1 - (c.embedding <=> ?::vector)) >= ? "
        + "order by c.embedding <=> ?::vector "
        + "limit ?";

    private static final RowMapper<RetrievedChunk> ROW_MAPPER = (rs, rowNum) ->
        new RetrievedChunk(
            rs.getString("title"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getDouble("similarity"));

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingProvider embeddingProvider;

    public KnowledgeRetrievalService(JdbcTemplate jdbcTemplate, EmbeddingProvider embeddingProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingProvider = embeddingProvider;
    }

    /**
     * @return os chunks relevantes (até 5) ordenados por similaridade desc; lista vazia
     *         se nada passa do threshold (a IA não recebe contexto de documento).
     */
    public List<RetrievedChunk> retrieve(UUID companyId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        float[] queryEmbedding = embeddingProvider.embed(List.of(query), EmbeddingKind.QUERY).get(0);
        String literal = VectorLiterals.toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query(SEARCH, ROW_MAPPER,
            literal, companyId, literal, MATCH_THRESHOLD, literal, MATCH_COUNT);
    }
}
