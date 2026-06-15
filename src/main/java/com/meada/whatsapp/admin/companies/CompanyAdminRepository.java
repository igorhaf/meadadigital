package com.meada.whatsapp.admin.companies;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Acesso a {@code companies} para o painel super-admin. Opera como service_role
 * (BYPASSRLS) — visão GLOBAL de todas as empresas, que é justamente a autoridade do
 * super-admin (o RLS por tenant não se aplica a ele).
 *
 * <p>companies não tem soft delete (sem deleted_at), então não há filtro de deleted.
 */
@Repository
public class CompanyAdminRepository {

    private static final String FIND_ALL =
        "select id, name, slug, status, created_at, palette_id from companies order by created_at desc";

    private static final String INSERT =
        "insert into companies (name, slug, palette_id) values (?, ?, ?) "
        + "returning id, name, slug, status, created_at, palette_id";

    private static final RowMapper<CompanyResponse> ROW_MAPPER = (rs, rowNum) ->
        new CompanyResponse(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("palette_id"));

    private final JdbcTemplate jdbcTemplate;

    public CompanyAdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Todas as empresas, mais novas primeiro (interesse natural do painel admin). */
    public List<CompanyResponse> findAll() {
        return jdbcTemplate.query(FIND_ALL, ROW_MAPPER);
    }

    /**
     * Insere uma empresa e retorna o estado persistido (RETURNING — pega id/status/
     * created_at gerados pelo banco numa só ida). status assume o default 'active'.
     *
     * <p>palette_id é fornecido pelo super-admin no momento da criação (camada 5.1.a).
     *
     * <p>NÃO trata colisão de slug: a violação do UNIQUE em companies.slug propaga como
     * {@link org.springframework.dao.DuplicateKeyException}, que o
     * {@code CompanyAdminController} captura localmente e mapeia para 409.
     */
    public CompanyResponse insert(String name, String slug, String paletteId) {
        return jdbcTemplate.queryForObject(INSERT, ROW_MAPPER, name, slug, paletteId);
    }
}
