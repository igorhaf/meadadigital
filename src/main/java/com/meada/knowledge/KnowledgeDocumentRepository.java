package com.meada.knowledge;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code knowledge_documents}. Opera como service_role (o backend é o escritor;
 * o tenant lê via SDK sob RLS). O status acompanha o processamento síncrono da ingestão.
 */
@Repository
public class KnowledgeDocumentRepository {

    private static final RowMapper<KnowledgeDocument> ROW_MAPPER = (rs, rowNum) ->
        new KnowledgeDocument(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("title"),
            rs.getString("storage_path"),
            rs.getString("status"),
            rs.getString("error_message"),
            rs.getInt("char_count"),
            rs.getInt("chunk_count"),
            rs.getBoolean("active"),
            ts(rs.getTimestamp("deleted_at")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private static java.time.Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static final String COLS =
        "id, company_id, title, storage_path, status, error_message, "
        + "char_count, chunk_count, active, deleted_at, created_at, updated_at";

    private static final String INSERT_PROCESSING =
        "insert into knowledge_documents (company_id, title, storage_path, status) "
        + "values (?, ?, ?, 'processing') returning " + COLS;

    private static final String UPDATE_READY =
        "update knowledge_documents set status = 'ready', char_count = ?, chunk_count = ?, "
        + "error_message = null, updated_at = now() where id = ?";

    private static final String UPDATE_FAILED =
        "update knowledge_documents set status = 'failed', error_message = ?, "
        + "updated_at = now() where id = ?";

    private static final String FIND_BY_COMPANY =
        "select " + COLS + " from knowledge_documents "
        + "where company_id = ? and deleted_at is null order by created_at desc";

    private static final String FIND_BY_ID =
        "select " + COLS + " from knowledge_documents "
        + "where id = ? and company_id = ? and deleted_at is null";

    private static final String SOFT_DELETE =
        "update knowledge_documents set deleted_at = now(), updated_at = now() "
        + "where id = ? and company_id = ? and deleted_at is null";

    private static final String SET_ACTIVE =
        "update knowledge_documents set active = ?, updated_at = now() "
        + "where id = ? and company_id = ? and deleted_at is null";

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public KnowledgeDocument insertProcessing(UUID companyId, String title, String storagePath) {
        return jdbcTemplate.queryForObject(INSERT_PROCESSING, ROW_MAPPER, companyId, title, storagePath);
    }

    public void updateReady(UUID id, int charCount, int chunkCount) {
        jdbcTemplate.update(UPDATE_READY, charCount, chunkCount, id);
    }

    public void updateFailed(UUID id, String errorMessage) {
        // Trunca a mensagem para não estourar nada e não vazar stack gigante no banco.
        String msg = errorMessage == null ? "unknown error"
            : errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage;
        jdbcTemplate.update(UPDATE_FAILED, msg, id);
    }

    public List<KnowledgeDocument> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(FIND_BY_COMPANY, ROW_MAPPER, companyId);
    }

    /**
     * Busca por id SCOPADA por tenant — o {@code company_id} no WHERE impede IDOR (ler documento
     * de outro tenant via UUID). Espelha o scoping de {@link #softDelete}/{@link #setActive}.
     */
    public Optional<KnowledgeDocument> findById(UUID id, UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(FIND_BY_ID, ROW_MAPPER, id, companyId).stream().findFirst();
    }

    /** @return true se algo foi deletado (documento existia e era do tenant). */
    public boolean softDelete(UUID id, UUID companyId) {
        return jdbcTemplate.update(SOFT_DELETE, id, companyId) > 0;
    }

    /** @return true se atualizou (documento existia e era do tenant). */
    public boolean setActive(UUID id, UUID companyId, boolean active) {
        return jdbcTemplate.update(SET_ACTIVE, active, id, companyId) > 0;
    }
}
