package com.meada.whatsapp.admin.companies;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa GET /admin/companies. 3 casos:
 * <ol>
 *   <li>super-admin → 200 + lista (ordenada por created_at DESC);
 *   <li>tenant-admin → 403 forbidden_not_super_admin (autorização — distinto do
 *       user_not_provisioned do filtro);
 *   <li>sem token → 401 missing_auth_header (coverage do endpoint pelo filtro; a
 *       unidade do filtro já é testada no JwtAuthenticationFilterIntegrationTest, aqui
 *       confirma que ESTE endpoint está atrás dele).
 * </ol>
 */
class CompanyAdminControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Insere uma company com created_at explícito (determinismo da ordenação DESC). */
    private void seedCompany(String name, String slug, Instant createdAt) {
        jdbcTemplate.update(
            "insert into companies (id, name, slug, created_at) values (?, ?, ?, ?)",
            UUID.randomUUID(), name, slug, Timestamp.from(createdAt));
    }

    @Test
    @DisplayName("super-admin → 200 lista todas as empresas (mais novas primeiro)")
    void superAdmin_listsAllCompanies() throws Exception {
        Instant now = Instant.now();
        seedCompany("Empresa Antiga", "empresa-antiga", now.minusSeconds(3600));  // 1h atrás
        seedCompany("Empresa Nova", "empresa-nova", now);                          // agora

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            // ORDER BY created_at DESC → a mais nova vem primeiro
            .andExpect(jsonPath("$[0].name").value("Empresa Nova"))
            .andExpect(jsonPath("$[0].slug").value("empresa-nova"))
            .andExpect(jsonPath("$[1].name").value("Empresa Antiga"));
    }

    @Test
    @DisplayName("tenant-admin → 403 forbidden_not_super_admin")
    void tenantAdmin_returns403() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));
    }

    @Test
    @DisplayName("sem token → 401 missing_auth_header (endpoint atrás do filtro)")
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/admin/companies"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.reason").value("missing_auth_header"));
    }
}
