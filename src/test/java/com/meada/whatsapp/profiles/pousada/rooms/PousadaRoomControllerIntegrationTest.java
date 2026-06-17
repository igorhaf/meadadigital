package com.meada.whatsapp.profiles.pousada.rooms;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de quartos (camada 7.6): CRUD + toggle + profile guard 403.
 */
class PousadaRoomControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);

        mockMvc.perform(post("/api/pousada/rooms").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Standard\",\"capacity\":2,\"nightlyRateCents\":18000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Standard"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from pousada_rooms where name = 'Standard'", UUID.class);

        mockMvc.perform(get("/api/pousada/rooms").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/pousada/rooms/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"nightlyRateCents\":20000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nightlyRateCents").value(20000));

        mockMvc.perform(patch("/api/pousada/rooms/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/pousada/rooms/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 room_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        mockMvc.perform(get("/api/pousada/rooms/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("room_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-pousada (salon) → /api/pousada/rooms → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        mockMvc.perform(get("/api/pousada/rooms").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
