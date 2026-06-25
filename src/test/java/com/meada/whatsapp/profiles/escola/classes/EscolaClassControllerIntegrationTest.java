package com.meada.whatsapp.profiles.escola.classes;

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
 * Testa os endpoints de turmas (camada 8.19): POST cria (com remainingSlots), GET filtro por turno,
 * PATCH edita, DELETE, turno inválido → 400, DELETE em uso → 409 class_in_use, profile guard 403.
 */
class EscolaClassControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("POST cria → GET filtro por turno mostra 1 → PATCH edita → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        mockMvc.perform(post("/api/escola/classes").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Jardim I\",\"grade\":\"Infantil\",\"shift\":\"manha\","
                    + "\"capacity\":20,\"monthlyCents\":50000}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Jardim I"))
            .andExpect(jsonPath("$.remainingSlots").value(20));

        UUID id = jdbcTemplate.queryForObject("select id from escola_classes where name = 'Jardim I'", UUID.class);

        mockMvc.perform(get("/api/escola/classes?shift=manha").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/escola/classes/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"capacity\":25}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capacity").value(25));

        mockMvc.perform(delete("/api/escola/classes/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com turno inválido → 400 invalid_shift")
    void invalidShift() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        mockMvc.perform(post("/api/escola/classes").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"grade\":\"Infantil\",\"shift\":\"noite\","
                    + "\"capacity\":20,\"monthlyCents\":50000}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_shift"));
    }

    @Test
    @DisplayName("DELETE de turma com matrícula → 409 class_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        UUID classId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Pré I', 'Infantil', 'integral', 10, 60000)", classId, companyId);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999990201", "Responsável");
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Aluno')",
            studentId, companyId, contactId);
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Aluno', 'Pré I', 'Infantil', 'integral', 60000, 'ativa')",
            companyId, classId, studentId);

        mockMvc.perform(delete("/api/escola/classes/" + classId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("class_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-escola (dental) → /api/escola/classes → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/escola/classes").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
