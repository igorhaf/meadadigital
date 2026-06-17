package com.meada.whatsapp.profiles.legal.cases;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import com.meada.whatsapp.profiles.legal.LegalCnjValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Testa /api/legal/cases (camada 7.2): list+filter, detalhe com andamentos, patch status, add+delete andamento. */
class LegalCaseControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID clientId;
    private String token;

    private String validCnj() {
        String dv = LegalCnjValidator.computeCheckDigits("0710233" + "2025" + "8" + "07" + "0019");
        return "0710233" + dv + "2025" + "8" + "07" + "0019";
    }

    private void seed() {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("legal@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id='legal' where id=?", companyId);
        token = mintValidToken("legal@test.dev", sub);
        clientId = jdbcTemplate.queryForObject(
            "insert into legal_clients (company_id, name) values (?, 'Cliente C') returning id",
            UUID.class, companyId);
    }

    private UUID seedCase(String cnj, String status) {
        return jdbcTemplate.queryForObject(
            "insert into legal_cases (company_id, legal_client_id, cnj_number, title, status) "
                + "values (?, ?, ?, 'Ação Teste', ?) returning id",
            UUID.class, companyId, clientId, cnj, status);
    }

    @Test
    @DisplayName("GET lista (filtro status) + total")
    void listFilter() throws Exception {
        seed();
        seedCase(validCnj(), "ativo");
        mockMvc.perform(get("/api/legal/cases?status=ativo").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("ativo"))
            .andExpect(jsonPath("$.items[0].cnjNumberFormatted").value(org.hamcrest.Matchers.containsString("-")));
    }

    @Test
    @DisplayName("detalhe mostra andamentos (timeline)")
    void detailWithUpdates() throws Exception {
        seed();
        UUID caseId = seedCase(validCnj(), "ativo");
        jdbcTemplate.update("insert into legal_case_updates (legal_case_id, title, occurred_at) "
            + "values (?, 'Petição inicial protocolada', now() - interval '10 days')", caseId);

        mockMvc.perform(get("/api/legal/cases/" + caseId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updates.length()").value(1))
            .andExpect(jsonPath("$.updates[0].title").value("Petição inicial protocolada"));
    }

    @Test
    @DisplayName("POST cria: CNJ inválido → 400; válido → 201; duplicado → 409; status inválido → 400")
    void createAndStatus() throws Exception {
        seed();
        String cnj = validCnj();
        // inválido
        mockMvc.perform(post("/api/legal/cases").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"legalClientId\":\"" + clientId + "\",\"cnjNumber\":\"00000000000000000000\",\"title\":\"X\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_cnj"));
        // válido
        mockMvc.perform(post("/api/legal/cases").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"legalClientId\":\"" + clientId + "\",\"cnjNumber\":\"" + cnj + "\",\"title\":\"Ação Nova\"}"))
            .andExpect(status().isCreated());
        // duplicado
        mockMvc.perform(post("/api/legal/cases").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"legalClientId\":\"" + clientId + "\",\"cnjNumber\":\"" + cnj + "\",\"title\":\"Outra\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_cnj"));

        UUID caseId = jdbcTemplate.queryForObject("select id from legal_cases where title='Ação Nova'", UUID.class);
        // status válido
        mockMvc.perform(patch("/api/legal/cases/" + caseId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"arquivado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("arquivado"));
        // status inválido
        mockMvc.perform(patch("/api/legal/cases/" + caseId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"inexistente\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("POST andamento → 201; DELETE andamento → 204")
    void addAndDeleteUpdate() throws Exception {
        seed();
        UUID caseId = seedCase(validCnj(), "ativo");
        mockMvc.perform(post("/api/legal/cases/" + caseId + "/updates").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"title\":\"Audiência designada\"}"))
            .andExpect(status().isCreated());
        UUID updateId = jdbcTemplate.queryForObject(
            "select id from legal_case_updates where legal_case_id=?", UUID.class, caseId);
        mockMvc.perform(delete("/api/legal/cases/" + caseId + "/updates/" + updateId)
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());
    }
}
