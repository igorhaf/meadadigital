package com.meada.whatsapp.profiles.pet.appointments;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de agendamentos (camada 7.8): POST cria manual, 409 conflict_slot (mesmo
 * profissional), PATCH status, profile guard 403, species_mismatch 400.
 */
class PetAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedProfessional(UUID companyId, String name, boolean active) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name, active) values (?, ?, ?, ?)",
            id, companyId, name, active);
        return id;
    }

    private UUID seedService(UUID companyId, String name, int duration, String speciesRestriction) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, duration_minutes, species_restriction) "
            + "values (?, ?, ?, ?, ?)", id, companyId, name, duration, speciesRestriction);
        return id;
    }

    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            id, companyId, phone, name);
        return id;
    }

    private UUID seedAnimal(UUID companyId, UUID contactId, String name, String species) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, ?, ?)",
            id, companyId, contactId, name, species);
        return id;
    }

    @Test
    @DisplayName("POST cria → 201 agendado; GET filtro por profissional mostra 1")
    void createAndFilter() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID svc = seedService(companyId, "Banho", 45, null);
        UUID contactId = seedContact(companyId, "+5511999990091", "Joana");
        UUID animal = seedAnimal(companyId, contactId, "Rex", "cao");

        // 2026-07-01T12:00-03:00 BRT → 15:00 UTC; dentro de 09–19 BRT.
        mockMvc.perform(post("/api/pet/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + svc
                    + "\",\"animalId\":\"" + animal + "\",\"startAt\":\"2026-07-01T15:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendado"))
            .andExpect(jsonPath("$.professionalName").value("Carla"))
            .andExpect(jsonPath("$.animalName").value("Rex"));

        mockMvc.perform(get("/api/pet/appointments?professionalId=" + prof).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST mesmo profissional + mesmo horário → 409 conflict_slot (com detalhes)")
    void conflictSameProfessional() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID svc = seedService(companyId, "Banho", 45, null);
        UUID contactId = seedContact(companyId, "+5511999990092", "Joana");
        UUID animal1 = seedAnimal(companyId, contactId, "Rex", "cao");
        UUID animal2 = seedAnimal(companyId, contactId, "Bidu", "cao");

        mockMvc.perform(post("/api/pet/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + svc
                    + "\",\"animalId\":\"" + animal1 + "\",\"startAt\":\"2026-07-01T15:00:00Z\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/pet/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + svc
                    + "\",\"animalId\":\"" + animal2 + "\",\"startAt\":\"2026-07-01T15:15:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.animalName").value("Rex"));
    }

    @Test
    @DisplayName("POST serviço só gato + animal cao → 400 species_mismatch")
    void speciesMismatch() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID svc = seedService(companyId, "Tosa felina", 45, "gato");
        UUID contactId = seedContact(companyId, "+5511999990093", "Joana");
        UUID animal = seedAnimal(companyId, contactId, "Rex", "cao");

        mockMvc.perform(post("/api/pet/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"serviceId\":\"" + svc
                    + "\",\"animalId\":\"" + animal + "\",\"startAt\":\"2026-07-01T15:00:00Z\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("species_mismatch"));
    }

    @Test
    @DisplayName("PATCH status agendado→confirmado → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla", true);
        UUID svc = seedService(companyId, "Banho", 45, null);
        UUID contactId = seedContact(companyId, "+5511999990094", "Joana");
        UUID animal = seedAnimal(companyId, contactId, "Rex", "cao");

        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:00:00Z"));
        jdbcTemplate.update(
            "insert into pet_appointments (id, company_id, professional_id, professional_name, service_id, service_name, "
                + "animal_id, animal_name, animal_species, contact_id, tutor_name, start_at, duration_minutes, end_at, status) "
                + "values (?, ?, ?, 'Carla', ?, 'Banho', ?, 'Rex', 'cao', ?, 'Joana', ?, 45, ?, 'agendado')",
            apptId, companyId, prof, svc, animal, contactId, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T15:45:00Z")));

        mockMvc.perform(patch("/api/pet/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmado"));

        // confirmado → agendado é inválida.
        mockMvc.perform(patch("/api/pet/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendado\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("tenant NÃO-pet (dental) → /api/pet/appointments → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/pet/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
