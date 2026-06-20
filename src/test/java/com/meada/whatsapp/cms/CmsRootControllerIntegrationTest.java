package com.meada.whatsapp.cms;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o CMS do ROOT (CMS-root, B1): o super-admin (sem company) acessa /api/cms/** sem precisar de
 * feature flag — o CMS é EMBUTIDO — e opera sobre a COMPANY-ÂNCORA da plataforma (is_platform=true,
 * "Meada"). O TRUNCATE zera companies, então re-seedamos a âncora no @BeforeEach (a migration só roda
 * no boot do container).
 */
class CmsRootControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");
    private static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final String JSON = "application/json";

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    @BeforeEach
    void seedPlatformAnchor() {
        // re-cria a âncora (o TRUNCATE do AbstractIntegrationTest apagou).
        jdbcTemplate.update(
            "insert into companies (id, name, slug, profile_id, status, is_platform) "
                + "values (?, 'Meada', 'meada', 'generic', 'active', true) "
                + "on conflict (id) do update set is_platform = true",
            PLATFORM);
    }

    @Test
    @DisplayName("super-admin GET /api/cms/site → 200 (CMS embutido, sem feature flag) na âncora")
    void superAdmin_getsSite() throws Exception {
        mockMvc.perform(get("/api/cms/site").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.site.slug").value("meada"));
    }

    @Test
    @DisplayName("super-admin cria página + publica na âncora → reflete em GET")
    void superAdmin_pagesFlow() throws Exception {
        String t = superToken();
        mockMvc.perform(post("/api/cms/pages").header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"pageSlug\":\"home\",\"title\":\"Bem-vindo à Meada\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.isHome").value(true));

        UUID pageId = jdbcTemplate.queryForObject(
            "select id from cms_pages where company_id = ? and page_slug = 'home'", UUID.class, PLATFORM);

        mockMvc.perform(put("/api/cms/pages/" + pageId).header("Authorization", "Bearer " + t).contentType(JSON)
                .content("{\"title\":\"Meada\",\"blocks\":[{\"type\":\"hero\",\"props\":{\"title\":\"Meada\"}}],\"published\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blocks.length()").value(1));

        mockMvc.perform(get("/api/cms/site").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages.length()").value(1));
    }

    @Test
    @DisplayName("sem token → 401 (o /api/cms/** continua autenticado)")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/api/cms/site")).andExpect(status().isUnauthorized());
    }
}
