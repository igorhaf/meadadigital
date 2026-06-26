package com.meada.whatsapp.profiles.dermatologia.proceduretypes;

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

/**
 * Testa os endpoints de tipos de atendimento (camada 8.11, ESCAPADA): CRUD, invalid_duration 400,
 * profile guard 403.
 */
class DermatologiaProcedureTypeControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria (duração+preparo) → GET lista → PATCH edita → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);

        mockMvc.perform(post("/api/dermatologia/procedure-types").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Botox\",\"durationMinutes\":60,\"prepInstructions\":\"Sem maquiagem.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Botox"))
            .andExpect(jsonPath("$.durationMinutes").value(60))
            .andExpect(jsonPath("$.prepInstructions").value("Sem maquiagem."));

        UUID id = jdbcTemplate.queryForObject("select id from dermatologia_procedure_types where name = 'Botox'", UUID.class);

        mockMvc.perform(get("/api/dermatologia/procedure-types").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/dermatologia/procedure-types/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"durationMinutes\":45}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.durationMinutes").value(45));

        mockMvc.perform(patch("/api/dermatologia/procedure-types/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/dermatologia/procedure-types/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com duração 0 → 400 invalid_duration")
    void invalidDuration() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);

        mockMvc.perform(post("/api/dermatologia/procedure-types").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Curta\",\"durationMinutes\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_duration"));
    }

    @Test
    @DisplayName("tenant NÃO-dermatologia (pet) → /api/dermatologia/procedure-types → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/dermatologia/procedure-types").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
