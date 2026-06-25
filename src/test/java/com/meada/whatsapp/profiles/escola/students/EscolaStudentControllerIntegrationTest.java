package com.meada.whatsapp.profiles.escola.students;

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
 * Testa os endpoints de alunos (camada 8.19) — sub-entidade do responsável (contact): POST cria,
 * POST sem contato válido → 404 contact_not_found, GET filtro por contactId, PATCH /archive,
 * DELETE em uso → 409 student_in_use, profile guard 403.
 */
class EscolaStudentControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
    @DisplayName("POST cria → GET filtro por contactId mostra 1 → archive → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990310", "Responsável");

        mockMvc.perform(post("/api/escola/students").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + contactId + "\",\"name\":\"Lucas\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lucas"));

        UUID id = jdbcTemplate.queryForObject("select id from escola_students where name = 'Lucas'", UUID.class);

        mockMvc.perform(get("/api/escola/students?contactId=" + contactId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/escola/students/" + id + "/archive").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/escola/students/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com contactId inexistente → 404 contact_not_found")
    void unknownContact() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);

        mockMvc.perform(post("/api/escola/students").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"contactId\":\"" + UUID.randomUUID() + "\",\"name\":\"Lucas\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("contact_not_found"));
    }

    @Test
    @DisplayName("DELETE de aluno com matrícula → 409 student_in_use")
    void deleteInUse() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        UUID contactId = seedContact(companyId, "+5511999990311", "Responsável");

        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Lucas')",
            studentId, companyId, contactId);
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Jardim I', 'Infantil', 'manha', 20, 50000)", classId, companyId);
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Lucas', 'Jardim I', 'Infantil', 'manha', 50000, 'ativa')",
            companyId, classId, studentId);

        mockMvc.perform(delete("/api/escola/students/" + studentId).header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("student_in_use"));
    }

    @Test
    @DisplayName("tenant NÃO-escola (dental) → /api/escola/students → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/escola/students").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
