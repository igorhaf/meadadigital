package com.meada.whatsapp.admin.companies;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa /admin/companies (GET lista + POST cria). Casos:
 * <ol>
 *   <li>GET super-admin → 200 + lista (ordenada por created_at DESC);
 *   <li>GET tenant-admin → 403 forbidden_not_super_admin (autorização — distinto do
 *       user_not_provisioned do filtro);
 *   <li>GET sem token → 401 missing_auth_header (coverage do endpoint pelo filtro; a
 *       unidade do filtro já é testada no JwtAuthenticationFilterIntegrationTest, aqui
 *       confirma que ESTE endpoint está atrás dele).
 *   <li>POST super-admin válido → 201 + persistida (com paletteId);
 *   <li>POST tenant-admin → 403 (nada persistido);
 *   <li>POST slug duplicado → 409 slug_already_exists (não duplica);
 *   <li>POST payload inválido (slug) → 400 (nada persistido);
 *   <li>POST paletteId em branco → 400 (validação @NotBlank; nada persistido);
 *   <li>POST paletteId ausente do body → 400 (validação @NotBlank cobre ausente também).
 * </ol>
 */
class CompanyAdminControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Insere uma company com created_at + palette_id explícitos (determinismo da
     *  ordenação DESC e da asserção de paletteId no GET). */
    private void seedCompany(String name, String slug, Instant createdAt, String paletteId) {
        jdbcTemplate.update(
            "insert into companies (id, name, slug, created_at, palette_id) values (?, ?, ?, ?, ?)",
            UUID.randomUUID(), name, slug, Timestamp.from(createdAt), paletteId);
    }

    @Test
    @DisplayName("super-admin → 200 lista todas as empresas (mais novas primeiro, com paletteId)")
    void superAdmin_listsAllCompanies() throws Exception {
        Instant now = Instant.now();
        seedCompany("Empresa Antiga", "empresa-antiga", now.minusSeconds(3600), "meada-default");
        seedCompany("Empresa Nova", "empresa-nova", now, "oceano");

        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(get("/admin/companies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            // ORDER BY created_at DESC → a mais nova vem primeiro
            .andExpect(jsonPath("$[0].name").value("Empresa Nova"))
            .andExpect(jsonPath("$[0].slug").value("empresa-nova"))
            .andExpect(jsonPath("$[0].paletteId").value("oceano"))
            .andExpect(jsonPath("$[1].name").value("Empresa Antiga"))
            .andExpect(jsonPath("$[1].paletteId").value("meada-default"));
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

    // ---- POST /admin/companies (4.2) ----------------------------------------

    @Test
    @DisplayName("super-admin cria empresa → 201 + persistida (com paletteId)")
    void create_superAdmin_returns201_andPersists() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Acme Corp"))
            .andExpect(jsonPath("$.slug").value("acme-corp"))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.createdAt").exists())
            .andExpect(jsonPath("$.paletteId").value("oceano"));

        String persistedPalette = jdbcTemplate.queryForObject(
            "select palette_id from companies where slug = ?", String.class, "acme-corp");
        assertThat(persistedPalette).isEqualTo("oceano");
    }

    @Test
    @DisplayName("tenant-admin tenta criar → 403 forbidden_not_super_admin (nada persistido)")
    void create_tenantAdmin_returns403() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_super_admin"));

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from companies where slug = ?", Long.class, "acme-corp");
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("slug duplicado → 409 slug_already_exists (não duplica)")
    void create_duplicateSlug_returns409() throws Exception {
        seedCompany("Já Existe", "acme-corp", Instant.now(), "meada-default");
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"oceano\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("slug_already_exists"));

        Long count = jdbcTemplate.queryForObject(
            "select count(*) from companies where slug = ?", Long.class, "acme-corp");
        assertThat(count).isEqualTo(1L);   // só a seedada
    }

    @Test
    @DisplayName("payload inválido (slug com maiúscula/espaço) → 400 (nada persistido)")
    void create_invalidPayload_returns400() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"Acme Corp\",\"paletteId\":\"oceano\"}"))   // slug inválido
            .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject("select count(*) from companies", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("paletteId em branco → 400 (validação @NotBlank; nada persistido)")
    void create_blankPaletteId_returns400() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\",\"paletteId\":\"\"}"))
            .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject("select count(*) from companies", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("paletteId ausente do body → 400")
    void create_missingPaletteId_returns400() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, SUB);
        mockMvc.perform(post("/admin/companies")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"name\":\"Acme Corp\",\"slug\":\"acme-corp\"}"))
            .andExpect(status().isBadRequest());

        Long count = jdbcTemplate.queryForObject("select count(*) from companies", Long.class);
        assertThat(count).isZero();
    }
}
