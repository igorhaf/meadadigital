package com.meada.whatsapp.profiles.pet.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config (camada 7.8): GET default 09:00–19:00, PUT atualiza, PUT inválido
 * (opens >= closes) → 400 invalid_hours, profile guard 403.
 */
class PetConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config → defaults 09:00–19:00")
    void getDefaults() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/pet/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("09:00:00"))
            .andExpect(jsonPath("$.closesAt").value("19:00:00"));
    }

    @Test
    @DisplayName("PUT atualiza a janela")
    void putUpdates() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(put("/api/pet/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"08:00\",\"closesAt\":\"20:00\",\"bufferMinutes\":0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("08:00:00"))
            .andExpect(jsonPath("$.closesAt").value("20:00:00"));
    }

    @Test
    @DisplayName("PUT opens >= closes → 400 invalid_hours")
    void putInvalidHours() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(put("/api/pet/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"19:00\",\"closesAt\":\"09:00\",\"bufferMinutes\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_hours"));
    }

    @Test
    @DisplayName("tenant NÃO-pet (dental) → /api/pet/config → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/pet/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
