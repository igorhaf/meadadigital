package com.meada.whatsapp.profiles.escola.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config da escola (camada 8.19): GET fallback defaults (07:00/18:00), PUT
 * upsert, janela inválida → 400 invalid_hours, hora malformada → 400 invalid_time, profile guard 403.
 */
class EscolaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem linha → defaults 07:00/18:00; PUT faz upsert")
    void getDefaultsAndPut() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        mockMvc.perform(get("/api/escola/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("07:00:00"))
            .andExpect(jsonPath("$.closesAt").value("18:00:00"));

        mockMvc.perform(put("/api/escola/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"businessName\":\"Escola Feliz\",\"opensAt\":\"06:30\","
                    + "\"closesAt\":\"19:00\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("Escola Feliz"))
            .andExpect(jsonPath("$.opensAt").value("06:30:00"));
    }

    @Test
    @DisplayName("PUT com janela inválida (opens >= closes) → 400 invalid_hours")
    void invalidHours() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        mockMvc.perform(put("/api/escola/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"18:00\",\"closesAt\":\"08:00\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_hours"));
    }

    @Test
    @DisplayName("PUT com hora malformada → 400 invalid_time")
    void invalidTime() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        mockMvc.perform(put("/api/escola/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"xx:yy\",\"closesAt\":\"19:00\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_time"));
    }

    @Test
    @DisplayName("tenant NÃO-escola (dental) → /api/escola/config → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/escola/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
