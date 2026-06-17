package com.meada.whatsapp.profiles.pousada.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de config (camada 7.6): PUT atualiza + profile guard 403.
 */
class PousadaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("PUT atualiza check-in/out + GET reflete; tenant não-pousada → 403")
    void putAndGuard() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);

        mockMvc.perform(put("/api/pousada/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"checkInTime\":\"15:00\",\"checkOutTime\":\"12:00\",\"cancellationPolicy\":\"Grátis até 5 dias.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkInTime").value("15:00:00"))
            .andExpect(jsonPath("$.cancellationPolicy").value("Grátis até 5 dias."));

        mockMvc.perform(get("/api/pousada/config").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkOutTime").value("12:00:00"));

        // tenant de outro perfil → 403.
        UUID sub2 = UUID.randomUUID();
        seedTenant(sub2, "sushi@test.dev", "sushi");
        String t2 = mintValidToken("sushi@test.dev", sub2);
        mockMvc.perform(get("/api/pousada/config").header("Authorization", "Bearer " + t2))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
