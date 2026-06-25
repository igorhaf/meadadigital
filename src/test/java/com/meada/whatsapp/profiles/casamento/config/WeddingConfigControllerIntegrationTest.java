package com.meada.whatsapp.profiles.casamento.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config do tenant casamento (camada 8.7): GET fallback (ausente → vazios),
 * PUT upsert, profile guard 403. SEM horário/slot — só nome da assessoria + notas. Clone do
 * AtelieConfigControllerIntegrationTest.
 */
class WeddingConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);

        mockMvc.perform(get("/api/casamento/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").doesNotExist());
    }

    @Test
    @DisplayName("PUT upsert grava businessName + notas e GET reflete")
    void putUpsert() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);

        mockMvc.perform(put("/api/casamento/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"businessName\":\"Assessoria da Bia\",\"notes\":\"Casamentos boutique\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("Assessoria da Bia"));

        mockMvc.perform(get("/api/casamento/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("Assessoria da Bia"))
            .andExpect(jsonPath("$.notes").value("Casamentos boutique"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        mockMvc.perform(get("/api/casamento/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
