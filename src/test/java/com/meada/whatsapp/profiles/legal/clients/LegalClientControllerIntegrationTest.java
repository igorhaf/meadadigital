package com.meada.whatsapp.profiles.legal.clients;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa /api/legal/clients (camada 7.2): CRUD + profile guard 403 + delete em uso 409. */
class LegalClientControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → PATCH edita → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);

        mockMvc.perform(post("/api/legal/clients").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Maria Silva\",\"email\":\"maria@x.com\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Maria Silva"));

        UUID id = jdbcTemplate.queryForObject("select id from legal_clients where name='Maria Silva'", UUID.class);

        mockMvc.perform(get("/api/legal/clients?search=maria").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/legal/clients/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"phone\":\"+5511999990000\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.phone").value("+5511999990000"));

        mockMvc.perform(delete("/api/legal/clients/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de cliente com processo → 409 client_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        UUID clientId = jdbcTemplate.queryForObject(
            "insert into legal_clients (company_id, name) values (?, 'Com Processo') returning id",
            UUID.class, companyId);
        jdbcTemplate.update("insert into legal_cases (company_id, legal_client_id, cnj_number, title) "
            + "values (?, ?, '07102331520258070019', 'Ação') ", companyId, clientId);

        mockMvc.perform(delete("/api/legal/clients/" + clientId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("client_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-legal (sushi) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/legal/clients").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
