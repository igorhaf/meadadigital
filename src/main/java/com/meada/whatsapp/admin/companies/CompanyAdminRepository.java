package com.meada.whatsapp.admin.companies;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Acesso de leitura a {@code companies} para o painel super-admin. Opera como
 * service_role (BYPASSRLS) — visão GLOBAL de todas as empresas, que é justamente a
 * autoridade do super-admin (o RLS por tenant não se aplica a ele).
 *
 * <p>Read-only por ora (a 4.2 adiciona o insert de criação de empresa). companies não
 * tem soft delete (sem deleted_at), então não há filtro de deleted.
 */
@Repository
public class CompanyAdminRepository {

    private static final String FIND_ALL =
        "select id, name, slug, status, created_at from companies order by created_at desc";

    private static final RowMapper<CompanyResponse> ROW_MAPPER = (rs, rowNum) ->
        new CompanyResponse(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public CompanyAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Todas as empresas, mais novas primeiro (interesse natural do painel admin). */
    public List<CompanyResponse> findAll() {
        return jdbcTemplate.query(FIND_ALL, ROW_MAPPER);
    }
}
