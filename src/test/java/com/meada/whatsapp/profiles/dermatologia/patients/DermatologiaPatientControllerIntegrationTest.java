package com.meada.whatsapp.profiles.dermatologia.patients;

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
 * Testa os endpoints de pacientes (camada 8.11): POST cria, GET com filtro de contactId, PATCH edita,
 * PATCH /archive, DELETE, profile guard 403, DELETE em uso → 409 patient_in_use.
 */
class DermatologiaPatientControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
    @DisplayName("POST cria → GET filtro por contactId mostra 1 → PATCH edita → archive → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990181", "Marina");

        mockMvc.perform(post("/api/dermatologia/patients").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"name\":\"Marina\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Marina"));

        UUID id = jdbcTemplate.queryForObject("select id from dermatologia_patients where name = 'Marina'", UUID.class);

        mockMvc.perform(get("/api/dermatologia/patients?contactId=" + contactId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/dermatologia/patients/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Marina Silva\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Marina Silva"));

        mockMvc.perform(patch("/api/dermatologia/patients/" + id + "/archive").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/dermatologia/patients/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE de paciente com consulta → 409 patient_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "derma@test.dev", "dermatologia");
        String t = mintValidToken("derma@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990182", "Marina");

        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientId, companyId, contactId);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, companyId);
        UUID type = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) values (?, ?, 'Consulta', 30)",
            type, companyId);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into dermatologia_appointments (company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Marina', 'Carla', 'Consulta', 30, ?, ?, 'agendada')",
            companyId, prof, patientId, type, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        mockMvc.perform(delete("/api/dermatologia/patients/" + patientId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("patient_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-dermatologia (pet) → /api/dermatologia/patients → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/dermatologia/patients").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
