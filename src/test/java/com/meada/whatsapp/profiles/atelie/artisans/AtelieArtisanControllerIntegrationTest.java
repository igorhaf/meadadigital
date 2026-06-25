package com.meada.whatsapp.profiles.atelie.artisans;

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
 * Testa os endpoints de artesãos (camada 8.14): CRUD + toggle + delete-em-uso 409 artisan_in_use +
 * profile guard 403. Clone do EventPlannerControllerIntegrationTest.
 */
class AtelieArtisanControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);

        mockMvc.perform(post("/api/atelie/artisans").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Beatriz\",\"specialty\":\"costura\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Beatriz"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from atelie_artisans where name = 'Beatriz'", UUID.class);

        mockMvc.perform(get("/api/atelie/artisans").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/atelie/artisans/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"specialty\":\"alfaiataria\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.specialty").value("alfaiataria"));

        mockMvc.perform(patch("/api/atelie/artisans/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/atelie/artisans/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de artesão atribuído a proposta → 409 artisan_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);

        UUID artisanId = jdbcTemplate.queryForObject(
            "insert into atelie_artisans (company_id, name) values (?, 'Beatriz') returning id", UUID.class, companyId);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999990171", "Cliente");
        jdbcTemplate.update(
            "insert into atelie_proposals (company_id, contact_id, artisan_id, customer_name, project_type, total_cents, status) "
                + "values (?, ?, ?, 'Cliente', 'costura', 0, 'rascunho')",
            companyId, contactId, artisanId);

        mockMvc.perform(delete("/api/atelie/artisans/" + artisanId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("artisan_in_use"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        mockMvc.perform(get("/api/atelie/artisans").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
