package com.meada.whatsapp.profiles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolve a COMPANY-ÂNCORA da plataforma (migration 44) — a company marcada {@code is_platform=true}
 * (o "Meada" do root). É onde vive o site institucional (CMS) editável pelo super-admin no painel.
 *
 * <p>O id é resolvido por {@code is_platform} (não hardcoded no código — o índice parcial UNIQUE
 * garante que há no máximo uma). Cacheado em memória após a 1ª leitura (a âncora é fixa, criada na
 * migration; não muda em runtime).
 */
@Component
public class PlatformCompany {

    private final JdbcTemplate jdbcTemplate;
    private volatile UUID cachedId;

    public PlatformCompany(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Lançada quando a company-âncora não existe (migration 44 não aplicada). */
    public static class PlatformCompanyMissingException extends RuntimeException {}

    /** O id da company-âncora da plataforma. Lança {@link PlatformCompanyMissingException} se ausente. */
    public UUID companyId() {
        UUID id = cachedId;
        if (id != null) {
            return id;
        }
        id = jdbcTemplate.query(
                "select id from companies where is_platform = true limit 1",
                (rs, rn) -> (UUID) rs.getObject("id"))
            .stream().findFirst().orElseThrow(PlatformCompanyMissingException::new);
        cachedId = id;
        return id;
    }
}
