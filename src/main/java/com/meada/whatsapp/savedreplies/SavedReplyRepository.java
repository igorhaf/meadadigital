package com.meada.whatsapp.savedreplies;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Acesso a {@code saved_replies} (camada 5.22 #88). Opera como service_role (o backend é o
 * escritor; o RLS isola por company mas não se aplica a service_role). Isolamento por
 * companyId no WHERE de toda operação — defesa em profundidade.
 */
@Repository
public class SavedReplyRepository {

    private static final RowMapper<SavedReply> ROW_MAPPER = (rs, rowNum) ->
        new SavedReply(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("title"),
            rs.getString("body"),
            rs.getTimestamp("created_at").toInstant());

    private static final String COLUMNS = "id, company_id, title, body, created_at";

    private final JdbcTemplate jdbcTemplate;

    public SavedReplyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Insere uma resposta pronta e retorna a linha criada (RETURNING). */
    public SavedReply insert(UUID companyId, String title, String body) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(body, "body must not be null");
        return jdbcTemplate.queryForObject(
            "insert into saved_replies (company_id, title, body) values (?, ?, ?) "
                + "returning " + COLUMNS,
            ROW_MAPPER, companyId, title, body);
    }

    /** Respostas prontas da empresa, mais recentes primeiro. */
    public List<SavedReply> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from saved_replies where company_id = ? "
                + "order by created_at desc",
            ROW_MAPPER, companyId);
    }

    /**
     * Atualiza título e corpo. Só da própria empresa (companyId no WHERE).
     *
     * @return true se atualizou; false se não existe / outra empresa.
     */
    public boolean update(UUID id, UUID companyId, String title, String body) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(body, "body must not be null");
        int updated = jdbcTemplate.update(
            "update saved_replies set title = ?, body = ?, updated_at = now() "
                + "where id = ? and company_id = ?",
            title, body, id, companyId);
        return updated > 0;
    }

    /**
     * Remove uma resposta pronta. Só da própria empresa (companyId no WHERE).
     *
     * @return true se removeu; false se não existe / outra empresa.
     */
    public boolean delete(UUID id, UUID companyId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        int deleted = jdbcTemplate.update(
            "delete from saved_replies where id = ? and company_id = ?",
            id, companyId);
        return deleted > 0;
    }
}
