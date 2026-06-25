package com.meada.whatsapp.profiles.escola.enrollments;

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
 * Testa os endpoints de matrículas (camada 8.19): POST manual cria, GET list/detail, PATCH status
 * (ativa→cancelada), 404 (turma/matrícula inexistente), 409 (already_active, invalid_status_transition),
 * profile guard 403.
 */
class EscolaEnrollmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedClass(UUID companyId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, ?, 'Infantil', 'manha', 20, 50000)", id, companyId, name);
        return id;
    }

    private UUID seedStudent(UUID companyId, String name) {
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+551199999" + (1000 + name.hashCode() % 9000), name + " resp");
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, ?)",
            id, companyId, contactId, name);
        return id;
    }

    @Test
    @DisplayName("POST manual cria → GET list/detail → PATCH status cancelada")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        UUID classId = seedClass(companyId, "Jardim I");
        UUID studentId = seedStudent(companyId, "Lucas");

        mockMvc.perform(post("/api/escola/enrollments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"classId\":\"" + classId + "\",\"studentId\":\"" + studentId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ativa"))
            .andExpect(jsonPath("$.studentName").value("Lucas"));

        UUID id = jdbcTemplate.queryForObject("select id from escola_enrollments where student_id = ?",
            UUID.class, studentId);

        mockMvc.perform(get("/api/escola/enrollments").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(1));

        mockMvc.perform(get("/api/escola/enrollments/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()));

        mockMvc.perform(patch("/api/escola/enrollments/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"cancelada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("cancelada"));
    }

    @Test
    @DisplayName("POST com turma inexistente → 404 class_not_found")
    void classNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        UUID studentId = seedStudent(companyId, "Lucas");

        mockMvc.perform(post("/api/escola/enrollments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"classId\":\"" + UUID.randomUUID() + "\",\"studentId\":\"" + studentId + "\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("class_not_found"));
    }

    @Test
    @DisplayName("POST com aluno já matriculado nessa turma → 409 already_active")
    void alreadyActive() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        UUID classId = seedClass(companyId, "Jardim I");
        UUID studentId = seedStudent(companyId, "Lucas");
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Lucas', 'Jardim I', 'Infantil', 'manha', 50000, 'ativa')",
            companyId, classId, studentId);

        mockMvc.perform(post("/api/escola/enrollments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"classId\":\"" + classId + "\",\"studentId\":\"" + studentId + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("already_active"));
    }

    @Test
    @DisplayName("PATCH status com transição inválida (cancelada→ativa) → 409 invalid_status_transition")
    void invalidTransition() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        UUID classId = seedClass(companyId, "Jardim I");
        UUID studentId = seedStudent(companyId, "Lucas");
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_enrollments (id, company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, ?, 'Lucas', 'Jardim I', 'Infantil', 'manha', 50000, 'cancelada')",
            id, companyId, classId, studentId);

        mockMvc.perform(patch("/api/escola/enrollments/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"ativa\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("GET matrícula inexistente → 404 enrollment_not_found")
    void enrollmentNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "escola@test.dev", "escola");
        String t = mintValidToken("escola@test.dev", sub);
        mockMvc.perform(get("/api/escola/enrollments/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("enrollment_not_found"));
    }

    @Test
    @DisplayName("tenant NÃO-escola (dental) → /api/escola/enrollments → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "dental@test.dev", "dental");
        String t = mintValidToken("dental@test.dev", sub);
        mockMvc.perform(get("/api/escola/enrollments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
