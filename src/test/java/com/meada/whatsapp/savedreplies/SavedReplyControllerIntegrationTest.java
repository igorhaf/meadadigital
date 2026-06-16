package com.meada.whatsapp.savedreplies;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o CRUD de respostas prontas (/admin/saved-replies, camada 5.22 #88) via camada
 * HTTP. Cobre: create 201, list só da própria empresa, update, delete e sem auth → 401.
 */
class SavedReplyControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    @DisplayName("POST /admin/saved-replies autenticado (tenant) → 201 com id+title+body")
    void create_authenticated_returns201() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(post("/admin/saved-replies")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"title\":\"Saudacao\",\"body\":\"Ola, como posso ajudar?\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("Saudacao"))
            .andExpect(jsonPath("$.body").value("Ola, como posso ajudar?"));
    }

    @Test
    @DisplayName("GET /admin/saved-replies lista só as da própria empresa")
    void list_returnsOwnCompanyOnly() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        mockMvc.perform(post("/admin/saved-replies")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"title\":\"Minha\",\"body\":\"corpo\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/saved-replies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Minha"));
    }

    @Test
    @DisplayName("PUT /admin/saved-replies/{id} atualiza → 204")
    void update_returns204() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String id = new ObjectMapper()
            .readTree(mockMvc.perform(post("/admin/saved-replies")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"title\":\"Antigo\",\"body\":\"velho\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
            .get("id").asText();

        mockMvc.perform(put("/admin/saved-replies/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"title\":\"Novo\",\"body\":\"novo corpo\"}"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/saved-replies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Novo"))
            .andExpect(jsonPath("$[0].body").value("novo corpo"));
    }

    @Test
    @DisplayName("DELETE /admin/saved-replies/{id} remove → 204")
    void delete_returns204() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String id = new ObjectMapper()
            .readTree(mockMvc.perform(post("/admin/saved-replies")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"title\":\"Apagar\",\"body\":\"corpo\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
            .get("id").asText();

        mockMvc.perform(delete("/admin/saved-replies/" + id)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/admin/saved-replies").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /admin/saved-replies sem auth → 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/saved-replies"))
            .andExpect(status().isUnauthorized());
    }
}
