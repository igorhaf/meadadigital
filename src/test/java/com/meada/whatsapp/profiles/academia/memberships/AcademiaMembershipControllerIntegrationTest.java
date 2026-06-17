package com.meada.whatsapp.profiles.academia.memberships;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de matrículas (camada 7.7): POST 201, 409 class_full (capacity 1),
 * 400 no_classes, PATCH status (transição inválida → 409).
 */
class AcademiaMembershipControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedPlan(UUID companyId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal', 20000)", id, companyId);
        return id;
    }

    private UUID seedClass(UUID companyId, String name, int capacity) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
            + "duration_minutes, capacity) values (?, ?, ?, 'funcional', 1, '07:00', 60, ?)", id, companyId, name, capacity);
        return id;
    }

    @Test
    @DisplayName("POST cria matrícula → 201 ativa com a aula")
    void create201() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);
        UUID plan = seedPlan(companyId);
        UUID cls = seedClass(companyId, "Funcional", 12);

        mockMvc.perform(post("/api/academia/memberships").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"planId\":\"" + plan + "\",\"classIds\":[\"" + cls + "\"],\"studentName\":\"Pedro\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ativa"))
            .andExpect(jsonPath("$.classes.length()").value(1));
    }

    @Test
    @DisplayName("POST em aula lotada (capacity 1) → 409 class_full com className")
    void classFull() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);
        UUID plan = seedPlan(companyId);
        UUID tiny = seedClass(companyId, "Spinning", 1);

        mockMvc.perform(post("/api/academia/memberships").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"planId\":\"" + plan + "\",\"classIds\":[\"" + tiny + "\"],\"studentName\":\"A\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/academia/memberships").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"planId\":\"" + plan + "\",\"classIds\":[\"" + tiny + "\"],\"studentName\":\"B\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("class_full"))
            .andExpect(jsonPath("$.className").value("Spinning"));
    }

    @Test
    @DisplayName("POST com array de aulas vazio → 400 no_classes")
    void noClasses() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);
        UUID plan = seedPlan(companyId);

        mockMvc.perform(post("/api/academia/memberships").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"planId\":\"" + plan + "\",\"classIds\":[],\"studentName\":\"X\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("no_classes"));
    }

    @Test
    @DisplayName("PATCH status ativa→cancelada → 200; cancelada→ativa (terminal) → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);
        UUID plan = seedPlan(companyId);
        UUID cls = seedClass(companyId, "Funcional", 12);

        // cria via API.
        mockMvc.perform(post("/api/academia/memberships").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"planId\":\"" + plan + "\",\"classIds\":[\"" + cls + "\"],\"studentName\":\"Pedro\"}"))
            .andExpect(status().isCreated());
        UUID id = jdbcTemplate.queryForObject("select id from academia_memberships where student_name = 'Pedro'", UUID.class);

        mockMvc.perform(patch("/api/academia/memberships/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"cancelada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("cancelada"));

        mockMvc.perform(patch("/api/academia/memberships/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"ativa\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
