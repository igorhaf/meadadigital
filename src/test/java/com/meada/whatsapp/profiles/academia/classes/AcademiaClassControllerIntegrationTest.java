package com.meada.whatsapp.profiles.academia.classes;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa os endpoints de aulas (camada 7.7): CRUD (com remainingSlots) + profile guard 403. */
class AcademiaClassControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("POST cria → 201 com remainingSlots=capacity; GET lista mostra 1")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);

        mockMvc.perform(post("/api/academia/classes").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Funcional\",\"modality\":\"funcional\",\"dayOfWeek\":1,"
                    + "\"startTime\":\"07:00\",\"durationMinutes\":60,\"capacity\":12}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Funcional"))
            .andExpect(jsonPath("$.remainingSlots").value(12));

        mockMvc.perform(get("/api/academia/classes").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("tenant NÃO-academia (sushi) → /api/academia/classes → 403")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/academia/classes").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
