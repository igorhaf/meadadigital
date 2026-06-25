package com.meada.whatsapp.profiles.escola.visits;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de visitas (camada 8.19, ESCAPADA 2): POST manual cria, GET list/detail, PATCH
 * status, data passada → 422 past_date, período inválido → 400 invalid_period, profile guard 403.
 */
class EscolaVisitControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("POST manual cria → GET list/detail → PATCH status realizada")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        String future = LocalDate.now(ZONE).plusDays(5).toString();

        mockMvc.perform(post("/api/escola/visits").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"visitorName\":\"Maria\",\"visitDate\":\"" + future
                    + "\",\"period\":\"manha\",\"numPeople\":2}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendada"))
            .andExpect(jsonPath("$.visitorName").value("Maria"));

        UUID id = jdbcTemplate.queryForObject("select id from escola_visits where visitor_name = 'Maria'", UUID.class);

        mockMvc.perform(get("/api/escola/visits").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/escola/visits/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(patch("/api/escola/visits/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"realizada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("realizada"));
    }

    @Test
    @DisplayName("POST com data passada → 422 past_date")
    void pastDate() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        String past = LocalDate.now(ZONE).minusDays(1).toString();

        mockMvc.perform(post("/api/escola/visits").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"visitorName\":\"Maria\",\"visitDate\":\"" + past
                    + "\",\"period\":\"manha\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.reason").value("past_date"));
    }

    @Test
    @DisplayName("POST com período inválido → 400 invalid_period")
    void invalidPeriod() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        String future = LocalDate.now(ZONE).plusDays(3).toString();

        mockMvc.perform(post("/api/escola/visits").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"visitorName\":\"Maria\",\"visitDate\":\"" + future
                    + "\",\"period\":\"noite\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_period"));
    }

    @Test
    @DisplayName("tenant NÃO-escola (dental) → /api/escola/visits → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/escola/visits").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
