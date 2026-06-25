package com.meada.whatsapp.profiles.atelie.proposals;

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
 * Testa os endpoints de propostas de ateliê (camada 8.14): POST abre, GET list/detalhe com
 * itens+provas, PATCH item recalcula total, PATCH status orcada, empty_budget, proposal_locked, 409
 * transição inválida, PROVAS/AJUSTES (POST/PATCH/DELETE/reorder/status) ordenadas por position via
 * HTTP, fitting_not_found, invalid_fitting_status, profile guard 403. Clone do
 * EventProposalControllerIntegrationTest com a escapada das provas.
 */
class AtelieProposalControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID openProposalId(String token) throws Exception {
        mockMvc.perform(post("/api/atelie/proposals").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"customerName\":\"Pedro\",\"projectType\":\"costura\","
                    + "\"briefing\":\"Terno sob medida\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("rascunho"))
            .andExpect(jsonPath("$.totalCents").value(0))
            .andExpect(jsonPath("$.projectType").value("costura"));
        return jdbcTemplate.queryForObject(
            "select id from atelie_proposals where customer_name = 'Pedro' order by opened_at desc limit 1", UUID.class);
    }

    @Test
    @DisplayName("POST abre rascunho; add item recalcula total; orcada com total>0")
    void openAndBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);

        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/atelie/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Tecido\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/atelie/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(500000));
        mockMvc.perform(post("/api/atelie/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Mão de obra\",\"quantity\":1,\"unitPriceCents\":300000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/atelie/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(800000));

        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("orcada"));
    }

    @Test
    @DisplayName("orçar proposta sem item → 400 empty_budget")
    void emptyBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("empty_budget"));
    }

    @Test
    @DisplayName("PATCH item numa proposta fechada → 409 proposal_locked")
    void proposalLocked() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        UUID id = openProposalId(t);
        mockMvc.perform(post("/api/atelie/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Tecido\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject(
            "select id from atelie_proposal_items where proposal_id = ?", UUID.class, id);
        jdbcTemplate.update("update atelie_proposals set status = 'fechada' where id = ?", id);

        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/items/" + itemId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"unitPriceCents\":999999}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("proposal_locked"));
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → 409 invalid_status_transition")
    void invalidTransition() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"aprovada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PROVAS: 3 provas via HTTP → detalhe ORDENADO por position; reorder inverte; total intacto")
    void fittingsOrderedAndReorderedViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/atelie/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"description\":\"Tecido\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/atelie/proposals/" + id + "/fittings").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"1ª prova\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/atelie/proposals/" + id + "/fittings").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"2ª prova\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/atelie/proposals/" + id + "/fittings").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Ajuste final\"}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/atelie/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fittings.length()").value(3))
            .andExpect(jsonPath("$.fittings[0].title").value("1ª prova"))
            .andExpect(jsonPath("$.fittings[1].title").value("2ª prova"))
            .andExpect(jsonPath("$.fittings[2].title").value("Ajuste final"))
            .andExpect(jsonPath("$.totalCents").value(500000));

        UUID f0 = jdbcTemplate.queryForObject(
            "select id from atelie_fittings where proposal_id = ? and title = '1ª prova'", UUID.class, id);
        UUID f1 = jdbcTemplate.queryForObject(
            "select id from atelie_fittings where proposal_id = ? and title = '2ª prova'", UUID.class, id);
        UUID f2 = jdbcTemplate.queryForObject(
            "select id from atelie_fittings where proposal_id = ? and title = 'Ajuste final'", UUID.class, id);

        // reorder: f2, f0, f1 → o detalhe reflete a nova ordem.
        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/fittings/reorder").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"orderedIds\":[\"" + f2 + "\",\"" + f0 + "\",\"" + f1 + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("Ajuste final"))
            .andExpect(jsonPath("$.items[1].title").value("1ª prova"))
            .andExpect(jsonPath("$.items[2].title").value("2ª prova"));
    }

    @Test
    @DisplayName("PATCH status da prova pendente→realizada grava completed_at; status inválido → 400")
    void fittingStatusTransitionViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/atelie/proposals/" + id + "/fittings").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"1ª prova\"}")).andExpect(status().isCreated());
        UUID fittingId = jdbcTemplate.queryForObject(
            "select id from atelie_fittings where proposal_id = ?", UUID.class, id);

        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/fittings/" + fittingId + "/status")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"status\":\"realizada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("realizada"))
            .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(patch("/api/atelie/proposals/" + id + "/fittings/" + fittingId + "/status")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"status\":\"xpto\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_fitting_status"));
    }

    @Test
    @DisplayName("DELETE de prova inexistente → 404 fitting_not_found")
    void deleteFittingNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(delete("/api/atelie/proposals/" + id + "/fittings/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("fitting_not_found"));
    }

    @Test
    @DisplayName("GET detalhe de proposta inexistente → 404 proposal_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);

        mockMvc.perform(get("/api/atelie/proposals/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("proposal_not_found"));
    }

    @Test
    @DisplayName("GET list devolve as propostas do tenant")
    void listProposals() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "atelie@test.dev", "atelie");
        String t = mintValidToken("atelie@test.dev", sub);
        openProposalId(t);

        mockMvc.perform(get("/api/atelie/proposals").header("Authorization", "Bearer " + t))
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

        mockMvc.perform(get("/api/atelie/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
