package com.meada.whatsapp.profiles.pet.services;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de serviços (camada 7.8): CRUD (com species_restriction, limpeza de restrição
 * e de preço via null no PATCH), toggle, profile guard 403, espécie inválida 400.
 */
class PetServiceControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST com speciesRestriction → GET lista → PATCH limpa restrição e preço (null) → toggle")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);

        mockMvc.perform(post("/api/pet/services").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Banho\",\"category\":\"Higiene\",\"durationMinutes\":60,"
                    + "\"priceCents\":5000,\"speciesRestriction\":\"gato\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Banho"))
            .andExpect(jsonPath("$.durationMinutes").value(60))
            .andExpect(jsonPath("$.speciesRestriction").value("gato"));

        UUID id = jdbcTemplate.queryForObject("select id from pet_services where name = 'Banho'", UUID.class);

        mockMvc.perform(get("/api/pet/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // PATCH speciesRestriction:null limpa a restrição.
        mockMvc.perform(patch("/api/pet/services/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"speciesRestriction\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.speciesRestriction").doesNotExist());

        // PATCH priceCents:null limpa o preço.
        mockMvc.perform(patch("/api/pet/services/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").doesNotExist());

        mockMvc.perform(patch("/api/pet/services/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("POST com espécie inválida → 400 invalid_species")
    void invalidSpecies() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(post("/api/pet/services").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Banho\",\"durationMinutes\":60,\"speciesRestriction\":\"passaro\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_species"));
    }

    @Test
    @DisplayName("tenant NÃO-pet (sushi) → /api/pet/services → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/pet/services").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
