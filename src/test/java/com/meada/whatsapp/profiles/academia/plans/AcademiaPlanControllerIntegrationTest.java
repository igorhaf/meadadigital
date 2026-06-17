package com.meada.whatsapp.profiles.academia.plans;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa os endpoints de planos (camada 7.7): CRUD + profile guard 403. */
class AcademiaPlanControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);

        mockMvc.perform(post("/api/academia/plans").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Mensal Livre\",\"monthlyCents\":20000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Mensal Livre"));

        UUID id = jdbcTemplate.queryForObject("select id from academia_plans where name = 'Mensal Livre'", UUID.class);

        mockMvc.perform(get("/api/academia/plans").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(delete("/api/academia/plans/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("tenant NÃO-academia (pousada) → /api/academia/plans → 403")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        mockMvc.perform(get("/api/academia/plans").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
