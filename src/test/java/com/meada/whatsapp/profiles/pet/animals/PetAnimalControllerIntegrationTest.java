package com.meada.whatsapp.profiles.pet.animals;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de animais (camada 7.8): POST cria, GET com filtro de espécie, PATCH edita,
 * PATCH /archive, DELETE, profile guard 403, DELETE em uso → 409 animal_in_use.
 */
class PetAnimalControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            id, companyId, phone, name);
        return id;
    }

    @Test
    @DisplayName("POST cria → GET filtro por espécie mostra 1 → PATCH edita → archive → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990081", "Tutor");

        mockMvc.perform(post("/api/pet/animals").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"name\":\"Rex\",\"species\":\"cao\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Rex"))
            .andExpect(jsonPath("$.species").value("cao"));

        UUID id = jdbcTemplate.queryForObject("select id from pet_animals where name = 'Rex'", UUID.class);

        mockMvc.perform(get("/api/pet/animals?species=cao").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/pet/animals/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"breed\":\"Labrador\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.breed").value("Labrador"));

        mockMvc.perform(patch("/api/pet/animals/" + id + "/archive").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/pet/animals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de animal com agendamento → 409 animal_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990082", "Tutor");

        UUID animalId = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Rex', 'cao')",
            animalId, companyId, contactId);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, companyId);
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, duration_minutes) values (?, ?, 'Banho', 60)",
            svc, companyId);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into pet_appointments (company_id, professional_id, professional_name, service_id, service_name, "
                + "animal_id, animal_name, animal_species, contact_id, tutor_name, start_at, duration_minutes, end_at, status) "
                + "values (?, ?, 'Carla', ?, 'Banho', ?, 'Rex', 'cao', ?, 'Tutor', ?, 60, ?, 'agendado')",
            companyId, prof, svc, animalId, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)));

        mockMvc.perform(delete("/api/pet/animals/" + animalId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("animal_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-pet (dental) → /api/pet/animals → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/pet/animals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
