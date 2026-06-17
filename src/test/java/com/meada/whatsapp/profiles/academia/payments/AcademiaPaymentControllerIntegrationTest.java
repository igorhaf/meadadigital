package com.meada.whatsapp.profiles.academia.payments;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa os endpoints de pagamentos (camada 7.7): POST registra + GET lista/summary. */
class AcademiaPaymentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("POST registra pagamento → 201; GET lista mostra 1 + summary")
    void recordAndList() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca@test.dev", "academia");
        String t = mintValidToken("aca@test.dev", sub);
        UUID plan = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal', 20000)", plan, companyId);
        UUID memb = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_memberships (id, company_id, plan_id, student_name, plan_name, plan_monthly_cents, start_date) "
            + "values (?, ?, ?, 'Aluno', 'Mensal', 20000, current_date)", memb, companyId, plan);

        String ref = LocalDate.now().withDayOfMonth(1).toString();
        mockMvc.perform(post("/api/academia/memberships/" + memb + "/payments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"referenceMonth\":\"" + ref + "\",\"amountCents\":20000,\"method\":\"Pix\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.amountCents").value(20000));

        mockMvc.perform(get("/api/academia/memberships/" + memb + "/payments").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.summary.totalPayments").value(1));
    }
}
