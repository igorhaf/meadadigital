package com.meada.whatsapp.profiles.dermatologia.appointments;

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
 * Testa os endpoints de consultas (camada 8.11): POST cria manual (snapshots tipo), 409 conflict_slot
 * (mesmo profissional, com detalhes), PATCH status (válido + inválido → 409), profile guard 403.
 */
class DermatologiaAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedProfessional(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name) values (?, ?, ?)", id, companyId, name);
        return id;
    }

    private UUID seedType(UUID companyId, String name, int dur) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) values (?, ?, ?, ?)",
            id, companyId, name, dur);
        return id;
    }

    private UUID seedContact(UUID companyId, String phone, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            id, companyId, phone, name);
        return id;
    }

    private UUID seedPatient(UUID companyId, UUID contactId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, ?)",
            id, companyId, contactId, name);
        return id;
    }

    @Test
    @DisplayName("POST cria → 201 agendada (snapshot tipo+duração); GET filtro por profissional mostra 1")
    void createAndFilter() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID type = seedType(companyId, "Consulta", 30);
        UUID contactId = seedContact(companyId, "+5511999990191", "Marina");
        UUID patient = seedPatient(companyId, contactId, "Marina");

        mockMvc.perform(post("/api/dermatologia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient
                    + "\",\"procedureTypeId\":\"" + type + "\",\"startAt\":\"2026-07-01T14:00:00Z\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendada"))
            .andExpect(jsonPath("$.professionalName").value("Carla"))
            .andExpect(jsonPath("$.patientName").value("Marina"))
            .andExpect(jsonPath("$.procedureTypeName").value("Consulta"))
            .andExpect(jsonPath("$.durationMinutes").value(30));

        mockMvc.perform(get("/api/dermatologia/appointments?professionalId=" + prof).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST mesmo profissional + mesmo horário → 409 conflict_slot (com detalhes)")
    void conflictSameProfessional() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID type = seedType(companyId, "Consulta", 30);
        UUID contactId = seedContact(companyId, "+5511999990192", "Marina");
        UUID patient1 = seedPatient(companyId, contactId, "Marina");
        UUID patient2 = seedPatient(companyId, contactId, "Bruno");

        mockMvc.perform(post("/api/dermatologia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient1
                    + "\",\"procedureTypeId\":\"" + type + "\",\"startAt\":\"2026-07-01T14:00:00Z\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/dermatologia/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof + "\",\"patientId\":\"" + patient2
                    + "\",\"procedureTypeId\":\"" + type + "\",\"startAt\":\"2026-07-01T14:15:00Z\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"))
            .andExpect(jsonPath("$.conflict.patientName").value("Marina"));
    }

    @Test
    @DisplayName("PATCH status agendada→confirmada → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);
        UUID prof = seedProfessional(companyId, "Carla");
        UUID type = seedType(companyId, "Consulta", 30);
        UUID contactId = seedContact(companyId, "+5511999990194", "Marina");
        UUID patient = seedPatient(companyId, contactId, "Marina");

        UUID apptId = UUID.randomUUID();
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T14:00:00Z"));
        jdbcTemplate.update(
            "insert into dermatologia_appointments (id, company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, ?, 'Marina', 'Carla', 'Consulta', 30, ?, ?, 'agendada')",
            apptId, companyId, prof, patient, type, contactId, start,
            java.sql.Timestamp.from(java.time.Instant.parse("2026-07-01T14:30:00Z")));

        mockMvc.perform(patch("/api/dermatologia/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmada"));

        // confirmada → agendada é inválida.
        mockMvc.perform(patch("/api/dermatologia/appointments/" + apptId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"agendada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("tenant NÃO-dermatologia (pet) → /api/dermatologia/appointments → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/dermatologia/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
