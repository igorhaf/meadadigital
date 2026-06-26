package com.meada.whatsapp.profiles;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa GET /admin/me/profile-match e GET /admin/profiles (camada 7.0): super-admin sempre
 * casa, tenant casa com o próprio perfil, mismatch devolve o subdomínio esperado, e
 * subdomínio inexistente → 400.
 */
class ProfileMatchControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID SUPER_SUB = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private String superToken() {
        return mintValidToken(SUPER_ADMIN_EMAIL, SUPER_SUB);
    }

    /** Provisiona um tenant cujo perfil de empresa é o dado (profile_id). Devolve o token. */
    private String seedTenantWithProfile(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return mintValidToken(email, sub);
    }

    @Test
    @DisplayName("GET /admin/profiles → catálogo com os 34 perfis (super-admin)")
    void profiles_catalog() throws Exception {
        mockMvc.perform(get("/admin/profiles").header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(34))
            .andExpect(jsonPath("$.items[?(@.id == 'legal')].productName").value("Legal"))
            .andExpect(jsonPath("$.items[?(@.id == 'restaurant')].productName").value("Restaurante"))
            .andExpect(jsonPath("$.items[?(@.id == 'salon')].productName").value("Salão"))
            .andExpect(jsonPath("$.items[?(@.id == 'pousada')].productName").value("Pousada"))
            .andExpect(jsonPath("$.items[?(@.id == 'academia')].productName").value("Academia"))
            .andExpect(jsonPath("$.items[?(@.id == 'pet')].productName").value("Pet"))
            .andExpect(jsonPath("$.items[?(@.id == 'oficina')].productName").value("Oficina"))
            .andExpect(jsonPath("$.items[?(@.id == 'nutri')].productName").value("Nutri"))
            .andExpect(jsonPath("$.items[?(@.id == 'barbearia')].productName").value("Barbearia"))
            .andExpect(jsonPath("$.items[?(@.id == 'eventos')].productName").value("Eventos"))
            .andExpect(jsonPath("$.items[?(@.id == 'estetica')].productName").value("Estética"))
            .andExpect(jsonPath("$.items[?(@.id == 'comida')].productName").value("Comida"))
            .andExpect(jsonPath("$.items[?(@.id == 'floricultura')].productName").value("Floricultura"))
            .andExpect(jsonPath("$.items[?(@.id == 'pizzaria')].productName").value("Pizzaria"))
            .andExpect(jsonPath("$.items[?(@.id == 'adega')].productName").value("Adega"))
            .andExpect(jsonPath("$.items[?(@.id == 'escola')].productName").value("Escola"))
            .andExpect(jsonPath("$.items[?(@.id == 'atelie')].productName").value("Ateliê"))
            .andExpect(jsonPath("$.items[?(@.id == 'casamento')].productName").value("Casamento"))
            .andExpect(jsonPath("$.items[?(@.id == 'concessionaria')].productName").value("Concessionária"))
            .andExpect(jsonPath("$.items[?(@.id == 'lavanderia')].productName").value("Lavanderia"))
            .andExpect(jsonPath("$.items[?(@.id == 'dermatologia')].productName").value("Dermatologia"))
            .andExpect(jsonPath("$.items[?(@.id == 'fotografia')].productName").value("Fotografia"))
            .andExpect(jsonPath("$.items[?(@.id == 'cursos')].productName").value("Cursos"))
            .andExpect(jsonPath("$.items[?(@.id == 'lingerie')].productName").value("Lingerie"))
            .andExpect(jsonPath("$.items[?(@.id == 'moda_infantil')].productName").value("Moda Infantil"))
            .andExpect(jsonPath("$.items[?(@.id == 'las')].productName").value("Lãs"))
            .andExpect(jsonPath("$.items[?(@.id == 'padaria')].productName").value("Padaria"))
            .andExpect(jsonPath("$.items[?(@.id == 'otica')].productName").value("Ótica"))
            .andExpect(jsonPath("$.items[?(@.id == 'papelaria')].productName").value("Papelaria"))
            .andExpect(jsonPath("$.items[?(@.id == 'viagens')].productName").value("Viagens"))
            .andExpect(jsonPath("$.items[?(@.id == 'suplementos')].productName").value("Suplementos"));
    }

    @Test
    @DisplayName("super-admin sempre match em qualquer subdomínio")
    void superAlwaysMatches() throws Exception {
        mockMvc.perform(get("/admin/me/profile-match?subdomain=dental")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.match").value(true))
            .andExpect(jsonPath("$.productName").value("Dental"));
    }

    @Test
    @DisplayName("tenant legal acessando 'juridico' → match true")
    void tenantMatchesOwnSubdomain() throws Exception {
        String t = seedTenantWithProfile(UUID.randomUUID(), TENANT_ADMIN_EMAIL, "legal");
        mockMvc.perform(get("/admin/me/profile-match?subdomain=juridico")
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.match").value(true))
            .andExpect(jsonPath("$.productName").value("Legal"));
    }

    @Test
    @DisplayName("tenant legal acessando 'dental' → match false + expectedSubdomain=juridico")
    void tenantMismatchReturnsExpected() throws Exception {
        String t = seedTenantWithProfile(UUID.randomUUID(), TENANT_ADMIN_EMAIL, "legal");
        mockMvc.perform(get("/admin/me/profile-match?subdomain=dental")
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.match").value(false))
            .andExpect(jsonPath("$.expectedSubdomain").value("juridico"))
            .andExpect(jsonPath("$.expectedProductName").value("Legal"));
    }

    @Test
    @DisplayName("subdomínio inexistente → 400 unknown_subdomain")
    void unknownSubdomain() throws Exception {
        mockMvc.perform(get("/admin/me/profile-match?subdomain=naoexiste")
                .header("Authorization", "Bearer " + superToken()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("unknown_subdomain"));
    }
}
