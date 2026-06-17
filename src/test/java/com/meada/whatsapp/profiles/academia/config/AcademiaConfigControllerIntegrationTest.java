package com.meada.whatsapp.profiles.academia.config;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa os endpoints de config (camada 7.7): PUT + profile guard 403. */
class AcademiaConfigControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("PUT atualiza + tenant não-academia → 403")
    void putAndGuard() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);
        mockMvc.perform(put("/api/academia/config").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"opensAt\":\"05:30\",\"closesAt\":\"23:00\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opensAt").value("05:30:00"));

        UUID sub2 = UUID.randomUUID();
        seedTenant(sub2, "salon@test.dev", "salon");
        String t2 = mintValidToken("salon@test.dev", sub2);
        mockMvc.perform(get("/api/academia/config").header("Authorization", "Bearer " + t2))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
