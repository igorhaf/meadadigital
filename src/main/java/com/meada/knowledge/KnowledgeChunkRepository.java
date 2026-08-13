package com.meada.knowledge;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * Acesso a {@code knowledge_chunks}. Inserção em batch dos chunks de um documento, com o
 * embedding gravado como vector via {@code ?::vector} (string literal montada de float[]).
 */
@Repository
public class KnowledgeChunkRepository {

    // embedding entra como texto '[...]' e o ::vector converte no banco. Sem pgvector-java.
    private static final String INSERT =
        "insert into knowledge_chunks (document_id, company_id, chunk_index, content, embedding) "
        + "values (?, ?, ?, ?, ?::vector)";

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertBatch(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                KnowledgeChunk c = chunks.get(i);
                ps.setObject(1, c.documentId());
                ps.setObject(2, c.companyId());
                ps.setInt(3, c.chunkIndex());
                ps.setString(4, c.content());
                ps.setString(5, VectorLiterals.toVectorLiteral(c.embedding()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }
}
