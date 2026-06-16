package com.meada.whatsapp.appointments;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de agendamento via camada HTTP (filtro + controller) — camada 5.19 #59.
 * Cobre o tenant (GET /admin/appointments lista só a própria empresa; PATCH muda status; sem auth
 * → 401). Modelado no AvailabilityControllerIntegrationTest (seedTenantAdmin + mintValidToken).
 */
class AppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("44444444-4444-4444-4444-444444444444");

    /** Semeia um contato + um appointment 'scheduled' na empresa, retorna o id do appointment. */
    private UUID seedAppointment(UUID companyId, Instant scheduledAt) {
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511977770001", "Cliente Cal");
        return jdbcTemplate.queryForObject(
            "insert into appointments (company_id, contact_id, scheduled_at) values (?, ?, ?) "
                + "returning id",
            UUID.class, companyId, contactId, Timestamp.from(scheduledAt));
    }

    @Test
    @DisplayName("GET /admin/appointments lista só agendamentos da própria empresa")
    void list_returnsOwnCompanyOnly() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        Instant when = Instant.now().plus(2, ChronoUnit.DAYS);
        seedAppointment(companyId, when);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant to = Instant.now().plus(30, ChronoUnit.DAYS);
        mockMvc.perform(get("/admin/appointments")
                .param("from", from.toString())
                .param("to", to.toString())
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("scheduled"))
            .andExpect(jsonPath("$[0].id").isNotEmpty());
    }

    @Test
    @DisplayName("PATCH /admin/appointments/{id} {status} → 200 e muda o status")
    void patchStatus_changesStatus() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        Instant when = Instant.now().plus(1, ChronoUnit.DAYS);
        UUID apptId = seedAppointment(companyId, when);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(patch("/admin/appointments/" + apptId)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"completed\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("completed"));

        String status = jdbcTemplate.queryForObject(
            "select status from appointments where id = ?", String.class, apptId);
        org.assertj.core.api.Assertions.assertThat(status).isEqualTo("completed");
    }

    @Test
    @DisplayName("PATCH com status inválido → 400 invalid_status")
    void patchStatus_invalid_returns400() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        UUID apptId = seedAppointment(companyId, Instant.now().plus(1, ChronoUnit.DAYS));
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(patch("/admin/appointments/" + apptId)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"status\":\"banana\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("GET /admin/appointments sem auth → 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/appointments"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /admin/appointments como super-admin → 403 (não é tenant-admin)")
    void list_superAdmin_returns403() throws Exception {
        String token = mintValidToken(SUPER_ADMIN_EMAIL, ADMIN_SUB);
        mockMvc.perform(get("/admin/appointments").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_not_tenant_admin"));
    }
}
