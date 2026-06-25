package com.meada.whatsapp.profiles.atelie.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config do ateliê (camada 8.14): GET fallback (ausente → vazios), PUT upsert,
 * profile guard 403. SEM horário/slot — só nome do ateliê + notas.
 */
class AtelieConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config gravada → fallback com businessName null")
    void getFallback() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);

        mockMvc.perform(get("/api/atelie/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").doesNotExist());
    }

    @Test
    @DisplayName("PUT upsert grava businessName + notas e GET reflete")
    void putUpsert() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);

        mockMvc.perform(put("/api/atelie/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"businessName\":\"Ateliê da Bia\",\"notes\":\"Atendimento com hora marcada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("Ateliê da Bia"));

        mockMvc.perform(get("/api/atelie/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("Ateliê da Bia"))
            .andExpect(jsonPath("$.notes").value("Atendimento com hora marcada"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        mockMvc.perform(get("/api/atelie/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
