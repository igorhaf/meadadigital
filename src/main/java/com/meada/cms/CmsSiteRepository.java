package com.meada.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code cms_sites} (SM-N) — config do site por tenant (1:1 com company). service_role.
 * Sempre faz JOIN com companies pra trazer o slug (base da URL pública).
 */
@Repository
public class CmsSiteRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CmsSiteRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    private RowMapper<CmsSite> mapper() {
        return (rs, rn) -> {
            JsonNode theme;
            try {
                String raw = rs.getString("theme");
                theme = raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
            } catch (Exception e) {
                theme = objectMapper.createObjectNode();
            }
            return new CmsSite(
                (UUID) rs.getObject("company_id"),
                rs.getString("slug"),
                rs.getString("domain"),
                rs.getBoolean("domain_verified"),
                rs.getString("verify_token"),
                rs.getBoolean("published"),
                theme,
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
        };
    }

    private static final String SELECT =
        "select s.company_id, c.slug, s.domain, s.domain_verified, s.verify_token, s.published, "
            + "s.theme, s.created_at, s.updated_at "
            + "from cms_sites s join companies c on c.id = s.company_id ";

    public Optional<CmsSite> findByCompany(UUID companyId) {
        return jdbcTemplate.query(SELECT + "where s.company_id = ?", mapper(), companyId).stream().findFirst();
    }

    /**
     * companyId a partir do slug da empresa (resolução pública /p/{slug}). null se não existe.
     * Empresa SUSPENSA é tratada como inexistente — mesmo contrato do resolvePublicCompany
     * (o site público some enquanto a suspensão durar).
     */
    public UUID companyIdBySlug(String slug) {
        return jdbcTemplate.query("select id from companies where slug = ? and status <> 'suspended'",
                (rs, rn) -> (UUID) rs.getObject("id"), slug)
            .stream().findFirst().orElse(null);
    }

    /** Linha de resolução pública por slug: perfil + status + se há site publicado. */
    public record PublicCompanyRow(String profileId, String status, boolean hasCms) {}

    /**
     * Resolução pública por slug (roteamento de domínios): a empresa existe? qual o perfil? tem
     * CMS publicado? Um SELECT com LEFT JOIN em cms_sites (has_cms = site existe E published).
     * Optional vazio se não há empresa com esse slug.
     */
    public Optional<PublicCompanyRow> resolveBySlug(String slug) {
        return jdbcTemplate.query(
                "select c.profile_id, c.status, "
                    + "coalesce(s.published, false) as has_cms "
                    + "from companies c "
                    + "left join cms_sites s on s.company_id = c.id "
                    + "where c.slug = ?",
                (rs, rn) -> new PublicCompanyRow(
                    rs.getString("profile_id"), rs.getString("status"), rs.getBoolean("has_cms")),
                slug)
            .stream().findFirst();
    }

    /**
     * Site publicado por domínio VERIFICADO (resolução pública por host custom). Empresa
     * SUSPENSA não serve site — e, por consequência, não recebe cert TLS on-demand
     * (domainAllowedForTls passa por aqui).
     */
    public Optional<CmsSite> findByVerifiedDomain(String domain) {
        return jdbcTemplate.query(SELECT + "where s.domain = ? and s.domain_verified = true and s.published = true "
                + "and c.status <> 'suspended'",
                mapper(), domain).stream().findFirst();
    }

    public void ensureExists(UUID companyId) {
        jdbcTemplate.update("insert into cms_sites (company_id) values (?) on conflict (company_id) do nothing", companyId);
    }

    public CmsSite setPublished(UUID companyId, boolean published) {
        ensureExists(companyId);
        jdbcTemplate.update("update cms_sites set published = ?, updated_at = now() where company_id = ?", published, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    public CmsSite setTheme(UUID companyId, String themeJson) {
        ensureExists(companyId);
        jdbcTemplate.update("update cms_sites set theme = ?::jsonb, updated_at = now() where company_id = ?", themeJson, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    /** Seta/limpa o domínio. Mexer no domínio ZERA a verificação (precisa re-verificar). */
    public CmsSite setDomain(UUID companyId, String domain) {
        ensureExists(companyId);
        jdbcTemplate.update(
            "update cms_sites set domain = ?, domain_verified = false, updated_at = now() where company_id = ?",
            domain, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    public CmsSite setVerifyToken(UUID companyId, String token) {
        ensureExists(companyId);
        jdbcTemplate.update("update cms_sites set verify_token = ?, updated_at = now() where company_id = ?", token, companyId);
        return findByCompany(companyId).orElseThrow();
    }

    public CmsSite setVerified(UUID companyId, boolean verified) {
        ensureExists(companyId);
        jdbcTemplate.update("update cms_sites set domain_verified = ?, updated_at = now() where company_id = ?", verified, companyId);
        return findByCompany(companyId).orElseThrow();
    }
}
