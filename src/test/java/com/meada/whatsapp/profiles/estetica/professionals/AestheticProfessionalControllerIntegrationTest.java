package com.meada.whatsapp.profiles.estetica.professionals;

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

/** Endpoints de profissionais (camada 8.3): CRUD + toggle + profile guard 403. */
class AestheticProfessionalControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "estetica@test.dev", "estetica");
        String t = mintValidToken("estetica@test.dev", sub);

        mockMvc.perform(post("/api/estetica/professionals").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Camila\",\"specialty\":\"facial\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Camila"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from aesthetic_professionals where name = 'Camila'", UUID.class);

        mockMvc.perform(get("/api/estetica/professionals").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/estetica/professionals/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"corporal\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("corporal"));

        mockMvc.perform(patch("/api/estetica/professionals/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/estetica/professionals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);

        mockMvc.perform(get("/api/estetica/professionals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
