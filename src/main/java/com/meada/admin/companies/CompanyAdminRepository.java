package com.meada.admin.companies;

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
        "select id, name, slug, status, created_at, palette_id, profile_id from companies "
            + "order by created_at desc";

    private static final String INSERT =
        "insert into companies (name, slug, palette_id) values (?, ?, ?) "
        + "returning id, name, slug, status, created_at, palette_id, profile_id";

    private static final RowMapper<CompanyResponse> ROW_MAPPER = (rs, rowNum) ->
        new CompanyResponse(
            (UUID) rs.getObject("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("palette_id"),
            rs.getString("profile_id"));

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

    /** Slug da empresa (base do subdomínio onde o impersonate abre o admin do tenant). null se não existe. */
    public String findSlug(UUID companyId) {
        return jdbcTemplate.query("select slug from companies where id = ?",
                (rs, rn) -> rs.getString("slug"), companyId)
            .stream().findFirst().orElse(null);
    }

    /**
     * Email do usuário-admin "owner" da empresa (mais antigo, ativo) — alvo do "entrar como
     * empresa" do super-admin. null se a empresa não tem admin elegível (suspensos/excluídos
     * são ignorados). Determinístico (order by created_at) p/ sempre escolher o mesmo.
     */
    public String findOwnerEmail(UUID companyId) {
        return jdbcTemplate.query(
                "select email from users where company_id = ? and role = 'admin' "
                    + "and suspended = false and deleted_at is null "
                    + "order by created_at asc limit 1",
                (rs, rn) -> rs.getString("email"),
                companyId)
            .stream().findFirst().orElse(null);
    }

    /** Token curto da empresa (compõe o email do tenant-admin). null se a empresa não existe. */
    public String findAdminToken(UUID companyId) {
        return jdbcTemplate.query("select admin_token from companies where id = ?",
                (rs, rn) -> rs.getString("admin_token"), companyId)
            .stream().findFirst().orElse(null);
    }

    /**
     * Insere a linha em public.users do tenant-admin recém-provisionado (id = uuid do Auth).
     * role 'admin'; palette_id usa o default da tabela (meada-default). Idempotente por (id).
     */
    public void insertTenantAdmin(UUID userId, UUID companyId, String email) {
        jdbcTemplate.update(
            "insert into public.users (id, company_id, email, role) values (?, ?, ?, 'admin') "
                + "on conflict (id) do update set company_id = excluded.company_id, "
                + "role = 'admin', email = excluded.email, updated_at = now()",
            userId, companyId, email);
    }
}
