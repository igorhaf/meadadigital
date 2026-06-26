package com.meada.whatsapp.profiles.dermatologia.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config do dermatologia (camada 8.11): GET default 08:00–18:00, PUT atualiza,
 * PUT com opens >= closes → 400 invalid_hours, profile guard 403.
 */
class DermatologiaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config → defaults 08:00–18:00 / buffer 0")
    void getDefault() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);

        mockMvc.perform(get("/api/dermatologia/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("08:00:00"))
            .andExpect(jsonPath("$.closesAt").value("18:00:00"))
            .andExpect(jsonPath("$.bufferMinutes").value(0));
    }

    @Test
    @DisplayName("PUT atualiza janela e buffer")
    void putUpdates() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);

        mockMvc.perform(put("/api/dermatologia/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"09:00\",\"closesAt\":\"17:00\",\"bufferMinutes\":15}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("09:00:00"))
            .andExpect(jsonPath("$.closesAt").value("17:00:00"))
            .andExpect(jsonPath("$.bufferMinutes").value(15));
    }

    @Test
    @DisplayName("PUT com opens >= closes → 400 invalid_hours")
    void putInvalidHours() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);

        mockMvc.perform(put("/api/dermatologia/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"18:00\",\"closesAt\":\"08:00\",\"bufferMinutes\":0}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_hours"));
    }

    @Test
    @DisplayName("tenant NÃO-dermatologia (pet) → /api/dermatologia/config → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/dermatologia/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
