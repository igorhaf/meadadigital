package com.meada.whatsapp.profiles.viagens.consultants;

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
 * Testa os endpoints de consultores (camada 8.18 / perfil viagens): CRUD + toggle + delete-em-uso 409
 * + profile guard 403. Espelho do EventPlannerControllerIntegrationTest (chassi eventos 8.2).
 */
class TravelConsultantControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);

        mockMvc.perform(post("/api/viagens/consultants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Beatriz\",\"specialty\":\"internacional\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Beatriz"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from travel_consultants where name = 'Beatriz'", UUID.class);

        mockMvc.perform(get("/api/viagens/consultants").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/viagens/consultants/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"nacional\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("nacional"));

        mockMvc.perform(patch("/api/viagens/consultants/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/viagens/consultants/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de consultor atribuído a proposta → 409 consultant_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);

        UUID consultantId = jdbcTemplate.queryForObject(
            "insert into travel_consultants (company_id, name) values (?, 'Beatriz') returning id", UUID.class, companyId);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999991293", "Cliente");
        jdbcTemplate.update(
            "insert into travel_proposals (company_id, contact_id, consultant_id, customer_name, status) "
                + "values (?, ?, ?, 'Cliente', 'rascunho')",
            companyId, contactId, consultantId);

        mockMvc.perform(delete("/api/viagens/consultants/" + consultantId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("consultant_in_use"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "barbearia@test.dev", "barbearia");
        String t = mintValidToken("barbearia@test.dev", sub);

        mockMvc.perform(get("/api/viagens/consultants").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
