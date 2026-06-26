package com.meada.whatsapp.profiles.viagens.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config do viagens (camada 8.18 / perfil viagens): GET fallback (ausente →
 * nulls), PUT upsert, profile guard 403. Espelho do EventConfig/lavanderia config controller test.
 */
class TravelConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("GET sem config gravada → fallback (businessName null)")
    void getFallback() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);

        mockMvc.perform(get("/api/viagens/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").doesNotExist())
            .andExpect(jsonPath("$.companyId").exists());
    }

    @Test
    @DisplayName("PUT upsert grava nome da agência + notas e GET reflete")
    void putUpsert() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);

        mockMvc.perform(put("/api/viagens/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"businessName\":\"Agência Modelo\",\"notes\":\"9h-18h\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.businessName").value("Agência Modelo"));

        mockMvc.perform(get("/api/viagens/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.notes").value("9h-18h"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);

        mockMvc.perform(get("/api/viagens/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
