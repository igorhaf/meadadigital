package com.meada.whatsapp.teams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de times (camada 5.20 #76) via camada HTTP (filtro + controller).
 * Cobre create 201, list só da própria empresa, update, delete e o caso sem auth → 401.
 * Modelado no {@link com.meada.whatsapp.admin.invitations.InvitationControllerIntegrationTest}.
 */
class TeamControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST /admin/teams autenticado (tenant) → 201 com id + name")
    void createTeam_authenticated_returns201() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(post("/admin/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Suporte\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.name").value("Suporte"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /admin/teams sem auth → 401")
    void createTeam_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/teams")
                .contentType("application/json")
                .content("{\"name\":\"Suporte\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /admin/teams como super-admin → 403 (não é tenant-admin)")
    void createTeam_superAdmin_returns403() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, ADMIN_SUB);
        mockMvc.perform(post("/admin/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Suporte\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_tenant_admin"));
    }

    @Test
    @DisplayName("GET /admin/teams lista só os times da própria empresa")
    void listTeams_returnsOwnCompanyOnly() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        // cria 1 time via o próprio endpoint.
        mockMvc.perform(post("/admin/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Vendas\"}"))
            .andExpect(status().isCreated());

        // semeia um time de OUTRA empresa direto no banco (service_role) — não deve aparecer.
        UUID otherCompany = UUID.randomUUID();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            otherCompany, "Outra", "outra-" + otherCompany);
        jdbcTemplate.update("insert into teams (company_id, name) values (?, ?)",
            otherCompany, "Time alheio");

        mockMvc.perform(get("/admin/teams").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Vendas"));
    }

    @Test
    @DisplayName("PUT /admin/teams/{id} renomeia o time → 200 com o novo nome")
    void updateTeam_renames_returns200() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String id = objectMapper.readTree(mockMvc.perform(post("/admin/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Antigo\"}"))
                .andReturn().getResponse().getContentAsString())
            .get("id").asText();

        mockMvc.perform(put("/admin/teams/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Novo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Novo"));
    }

    @Test
    @DisplayName("DELETE /admin/teams/{id} remove o time → 204")
    void deleteTeam_removes_returns204() throws Exception {
        seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String id = objectMapper.readTree(mockMvc.perform(post("/admin/teams")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"name\":\"Descartável\"}"))
                .andReturn().getResponse().getContentAsString())
            .get("id").asText();

        mockMvc.perform(delete("/admin/teams/" + id)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from teams where id = ?", Integer.class, UUID.fromString(id));
        assertThat(count).isZero();
    }
}
