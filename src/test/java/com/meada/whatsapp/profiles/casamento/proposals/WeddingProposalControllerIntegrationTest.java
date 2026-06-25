package com.meada.whatsapp.profiles.casamento.proposals;

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
 * Testa os endpoints de propostas de casamento (camada 8.7): POST abre, GET list/detalhe com
 * itens+cronograma+checklist, PATCH item recalcula total, PATCH status orcada, empty_budget,
 * proposal_locked, 409 transição inválida, CRONOGRAMA ordenado por start_time, CHECKLIST (POST/toggle/
 * ordenação por due_date), checklist_task_not_found, proposal_not_found, profile guard 403. Clone do
 * EventProposalControllerIntegrationTest + a escapada do checklist.
 */
class WeddingProposalControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID openProposalId(String token) throws Exception {
        mockMvc.perform(post("/api/casamento/proposals").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"customerName\":\"Pedro\",\"weddingStyle\":\"clássico\","
                    + "\"guestCount\":120,\"briefing\":\"Casamento ao ar livre\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("rascunho"))
            .andExpect(jsonPath("$.totalCents").value(0));
        return jdbcTemplate.queryForObject(
            "select id from wedding_proposals where customer_name = 'Pedro' order by opened_at desc limit 1", UUID.class);
    }

    @Test
    @DisplayName("POST abre rascunho; add item recalcula total; orcada com total>0")
    void openAndBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);

        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/casamento/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Espaço\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/casamento/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(500000));
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Buffet\",\"quantity\":1,\"unitPriceCents\":300000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/casamento/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(800000));

        mockMvc.perform(patch("/api/casamento/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("orcada"));
    }

    @Test
    @DisplayName("orçar proposta sem item → 400 empty_budget")
    void emptyBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/casamento/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("empty_budget"));
    }

    @Test
    @DisplayName("PATCH item numa proposta fechada → 409 proposal_locked")
    void proposalLocked() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Espaço\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject(
            "select id from wedding_proposal_items where proposal_id = ?", UUID.class, id);
        jdbcTemplate.update("update wedding_proposals set status = 'fechada' where id = ?", id);

        mockMvc.perform(patch("/api/casamento/proposals/" + id + "/items/" + itemId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"unitPriceCents\":999999}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("proposal_locked"));
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → 409 invalid_status_transition")
    void invalidTransition() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/casamento/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"aprovada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("CRONOGRAMA: 3 marcos fora de ordem via HTTP → detalhe ORDENADO por horário; total intacto")
    void timelineOrderedViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/casamento/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Espaço\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());

        // 3 marcos FORA de ordem: 22:00, 17:00, 19:00.
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/timeline").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"startTime\":\"22:00\",\"title\":\"Festa\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/timeline").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"startTime\":\"17:00\",\"title\":\"Cerimônia\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/timeline").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"startTime\":\"19:00\",\"title\":\"Jantar\"}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/casamento/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timeline.length()").value(3))
            .andExpect(jsonPath("$.timeline[0].title").value("Cerimônia"))
            .andExpect(jsonPath("$.timeline[1].title").value("Jantar"))
            .andExpect(jsonPath("$.timeline[2].title").value("Festa"))
            .andExpect(jsonPath("$.totalCents").value(500000));
    }

    @Test
    @DisplayName("CHECKLIST: tarefas com/sem prazo via HTTP → detalhe ORDENADO por due_date NULLS LAST; toggle marca")
    void checklistOrderedAndToggledViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);

        // FORA de ordem: sem prazo, prazo tardio, prazo cedo.
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/checklist").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Sem prazo\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/checklist").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Prazo tardio\",\"dueDate\":\"2026-12-01\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/checklist").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Prazo cedo\",\"dueDate\":\"2026-06-01\"}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/casamento/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checklist.length()").value(3))
            .andExpect(jsonPath("$.checklist[0].title").value("Prazo cedo"))
            .andExpect(jsonPath("$.checklist[1].title").value("Prazo tardio"))
            .andExpect(jsonPath("$.checklist[2].title").value("Sem prazo"))
            .andExpect(jsonPath("$.totalCents").value(0));

        UUID taskId = jdbcTemplate.queryForObject(
            "select id from wedding_checklist_tasks where proposal_id = ? and title = 'Prazo cedo'", UUID.class, id);
        mockMvc.perform(patch("/api/casamento/proposals/" + id + "/checklist/" + taskId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"done\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.doneAt").exists());
    }

    @Test
    @DisplayName("toggle de tarefa de checklist inexistente → 404 checklist_task_not_found")
    void checklistTaskNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/casamento/proposals/" + id + "/checklist/" + UUID.randomUUID() + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"done\":true}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("checklist_task_not_found"));
    }

    @Test
    @DisplayName("DELETE de tarefa de checklist numa proposta fechada → 409 proposal_locked")
    void checklistLocked() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        UUID id = openProposalId(t);
        mockMvc.perform(post("/api/casamento/proposals/" + id + "/checklist").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Enviar convites\"}")).andExpect(status().isCreated());
        UUID taskId = jdbcTemplate.queryForObject(
            "select id from wedding_checklist_tasks where proposal_id = ?", UUID.class, id);
        jdbcTemplate.update("update wedding_proposals set status = 'fechada' where id = ?", id);

        mockMvc.perform(delete("/api/casamento/proposals/" + id + "/checklist/" + taskId)
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("proposal_locked"));
    }

    @Test
    @DisplayName("GET detalhe de proposta inexistente → 404 proposal_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);

        mockMvc.perform(get("/api/casamento/proposals/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("proposal_not_found"));
    }

    @Test
    @DisplayName("GET list devolve as propostas do tenant")
    void listProposals() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "casamento@test.dev", "casamento");
        String t = mintValidToken("casamento@test.dev", sub);
        openProposalId(t);

        mockMvc.perform(get("/api/casamento/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "eventos@test.dev", "eventos");
        String t = mintValidToken("eventos@test.dev", sub);

        mockMvc.perform(get("/api/casamento/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
