package com.meada.teams;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Acesso a {@code teams} (camada 5.20 #76). Opera como service_role (o backend é o
 * escritor; o painel admin do tenant chama os endpoints REST que passam por aqui). O
 * isolamento por empresa vem do companyId no WHERE de cada operação (defesa em
 * profundidade — o RLS também isola, mas service_role não aplica RLS).
 */
@Repository
public class TeamRepository {

    private static final RowMapper<Team> ROW_MAPPER = (rs, rowNum) ->
        new Team(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant());

    private static final String COLUMNS = "id, company_id, name, created_at";

    private final JdbcTemplate jdbcTemplate;

    public TeamRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Insere um time e retorna a linha criada (RETURNING). */
    public Team insert(UUID companyId, String name) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        return jdbcTemplate.queryForObject(
            "insert into teams (company_id, name) values (?, ?) returning " + COLUMNS,
            ROW_MAPPER, companyId, name);
    }

    /** Times da empresa, mais recentes primeiro. Isolamento por companyId no WHERE. */
    public List<Team> findByCompany(UUID companyId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        return jdbcTemplate.query(
            "select " + COLUMNS + " from teams where company_id = ? order by created_at desc",
            ROW_MAPPER, companyId);
    }

    /**
     * Renomeia um time da própria empresa. Toca updated_at. Só da empresa do tenant
     * (companyId no WHERE).
     *
     * @return true se atualizou; false se não existe / outra empresa.
     */
    public boolean update(UUID id, UUID companyId, String name) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        int updated = jdbcTemplate.update(
            "update teams set name = ?, updated_at = now() where id = ? and company_id = ?",
            name, id, companyId);
        return updated > 0;
    }

    /**
     * Remove um time da própria empresa (DELETE físico). A FK
     * {@code conversations.team_id ON DELETE SET NULL} desassocia as conversas. Só da
     * empresa do tenant (companyId no WHERE).
     *
     * @return true se removeu; false se não existe / outra empresa.
     */
    public boolean delete(UUID id, UUID companyId) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(companyId, "companyId must not be null");
        int deleted = jdbcTemplate.update(
            "delete from teams where id = ? and company_id = ?", id, companyId);
        return deleted > 0;
    }
}
