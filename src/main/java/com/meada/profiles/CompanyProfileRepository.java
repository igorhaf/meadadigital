package com.meada.profiles;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Lookup mínimo do perfil de uma empresa (camada 7.0). Isolado do CompanyAdmin* (que é do
 * painel super-admin) porque o PromptBuilder, no fluxo de IA, só precisa do profile_id —
 * sem arrastar a superfície administrativa.
 */
@Repository
public class CompanyProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public CompanyProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** profile_id da empresa, ou "generic" se a empresa sumiu (fallback seguro). */
    public String findProfileId(UUID companyId) {
        return jdbcTemplate.query(
                "select profile_id from companies where id = ?",
                (rs, rn) -> rs.getString("profile_id"), companyId)
            .stream().findFirst().orElse("generic");
    }
}
