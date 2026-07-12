package com.meada.cms;

import com.meada.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o CMS público multi-página (SM-N): home/página por slug, por domínio verificado, e o ask de
 * TLS. Sem auth. Só serve publicado; rascunho/não-verificado → 404. View traz nav + tema.
 */
class CmsPublicControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CmsService service;

    private static final UUID CO = UUID.fromString("cf900000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')", CO, "Pública Co", "publica-co");
    }

    /** Cria home + uma sub-página, ambas publicadas, e publica o site. */
    private void seedPublishedSite() {
        CmsPage home = service.createPage(CO, "home", "Início");
        CmsPage svc = service.createPage(CO, "servicos", "Serviços");
        service.savePageContent(CO, home.id(), "Início", null, true);
        service.savePageContent(CO, svc.id(), "Serviços", null, true);
        service.setPublished(CO, true);
    }

    @Test
    @DisplayName("home publicada por slug → 200 com nav (2 páginas); sem campos internos")
    void homeBySlug() throws Exception {
        seedPublishedSite();
        mockMvc.perform(get("/public/cms/by-slug/publica-co"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Início"))
            .andExpect(jsonPath("$.nav.length()").value(2))
            .andExpect(jsonPath("$.published").doesNotExist());
    }

    @Test
    @DisplayName("página interna por slug → 200")
    void pageBySlug() throws Exception {
        seedPublishedSite();
        mockMvc.perform(get("/public/cms/by-slug/publica-co/servicos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Serviços"));
    }

    @Test
    @DisplayName("site em rascunho → 404; slug inexistente → 404")
    void draftOrUnknown_404() throws Exception {
        CmsPage home = service.createPage(CO, "home", "H");
        service.savePageContent(CO, home.id(), "H", null, true); // página publicada, mas site não
        mockMvc.perform(get("/public/cms/by-slug/publica-co")).andExpect(status().isNotFound());
        mockMvc.perform(get("/public/cms/by-slug/nao-existe")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("por domínio: só verificado+publicado serve; ask de TLS idem")
    void byDomainAndTls() throws Exception {
        seedPublishedSite();
        service.setDomain(CO, "publicaco.com.br");
        // ainda não verificado → 404 e TLS não permitido.
        mockMvc.perform(get("/public/cms/by-domain").param("host", "publicaco.com.br")).andExpect(status().isNotFound());
        mockMvc.perform(get("/public/cms/tls-allowed").param("domain", "publicaco.com.br")).andExpect(status().isNotFound());
        // marca verificado direto no banco (o DNS é testado no service).
        jdbcTemplate.update("update cms_sites set domain_verified = true where company_id = ?", CO);
        mockMvc.perform(get("/public/cms/by-domain").param("host", "publicaco.com.br")).andExpect(status().isOk());
        mockMvc.perform(get("/public/cms/tls-allowed").param("domain", "publicaco.com.br")).andExpect(status().isOk());
        mockMvc.perform(get("/public/cms/tls-allowed").param("domain", "outro.com")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("empresa SUSPENSA → 404 por slug, por domínio e no ask de TLS (site público some)")
    void suspendedCompany_404Everywhere() throws Exception {
        seedPublishedSite();
        service.setDomain(CO, "publicaco.com.br");
        jdbcTemplate.update("update cms_sites set domain_verified = true where company_id = ?", CO);
        // sanity: servindo normalmente antes da suspensão.
        mockMvc.perform(get("/public/cms/by-slug/publica-co")).andExpect(status().isOk());
        mockMvc.perform(get("/public/cms/by-domain").param("host", "publicaco.com.br")).andExpect(status().isOk());

        // suspensão pelo root → mesmo contrato do resolvePublicCompany: tratada como inexistente.
        jdbcTemplate.update("update companies set status = 'suspended' where id = ?", CO);
        mockMvc.perform(get("/public/cms/by-slug/publica-co")).andExpect(status().isNotFound());
        mockMvc.perform(get("/public/cms/by-slug/publica-co/servicos")).andExpect(status().isNotFound());
        mockMvc.perform(get("/public/cms/by-domain").param("host", "publicaco.com.br")).andExpect(status().isNotFound());
        mockMvc.perform(get("/public/cms/tls-allowed").param("domain", "publicaco.com.br")).andExpect(status().isNotFound());
    }
}
