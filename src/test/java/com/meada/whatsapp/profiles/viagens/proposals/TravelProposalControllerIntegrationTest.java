package com.meada.whatsapp.profiles.viagens.proposals;

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
 * Testa os endpoints de propostas (camada 8.18 / perfil viagens): POST abre, GET list/detalhe com
 * cotação+itinerário, PATCH item recalcula total, empty_budget, proposal_locked, 409 transição
 * inválida, ITINERÁRIO multi-dia via HTTP (add/reorder ordenado por day_date NULLS LAST + 404
 * itinerary_day_not_found + 400 invalid_date), profile guard 403. Espelho do
 * EventProposalControllerIntegrationTest (chassi eventos 8.2).
 */
class TravelProposalControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID openProposalId(String token) throws Exception {
        mockMvc.perform(post("/api/viagens/proposals").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"customerName\":\"Pedro\",\"destination\":\"Lisboa\","
                    + "\"numTravelers\":2,\"briefing\":\"Lua de mel\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("rascunho"))
            .andExpect(jsonPath("$.totalCents").value(0));
        return jdbcTemplate.queryForObject(
            "select id from travel_proposals where customer_name = 'Pedro' order by opened_at desc limit 1", UUID.class);
    }

    @Test
    @DisplayName("POST abre rascunho; add item recalcula total; orcada com total>0")
    void openAndBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);

        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/viagens/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"category\":\"aereo\",\"description\":\"Passagens\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/viagens/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(500000));
        mockMvc.perform(post("/api/viagens/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"category\":\"hospedagem\",\"description\":\"Hotel\",\"quantity\":1,\"unitPriceCents\":300000}"))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/api/viagens/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.totalCents").value(800000));

        mockMvc.perform(patch("/api/viagens/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("orcada"));
    }

    @Test
    @DisplayName("orçar proposta sem item → 400 empty_budget")
    void emptyBudget() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/viagens/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"orcada\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("empty_budget"));
    }

    @Test
    @DisplayName("PATCH item numa proposta fechada → 409 proposal_locked")
    void proposalLocked() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);
        mockMvc.perform(post("/api/viagens/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"category\":\"aereo\",\"description\":\"Passagens\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject(
            "select id from travel_proposal_items where proposal_id = ?", UUID.class, id);
        jdbcTemplate.update("update travel_proposals set status = 'fechada' where id = ?", id);

        mockMvc.perform(patch("/api/viagens/proposals/" + id + "/items/" + itemId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"unitPriceCents\":999999}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("proposal_locked"));
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → 409 invalid_status_transition")
    void invalidTransition() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/viagens/proposals/" + id + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"aprovada\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("ITINERÁRIO: 3 dias via HTTP (um sem data) → detalhe ORDENADO por day_date NULLS LAST; total intacto")
    void itineraryOrderedViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        // 1 item de cotação → total 500000.
        mockMvc.perform(post("/api/viagens/proposals/" + id + "/items").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"category\":\"aereo\",\"description\":\"Passagens\",\"quantity\":1,\"unitPriceCents\":500000}"))
            .andExpect(status().isCreated());

        // 3 dias FORA de ordem; um SEM data (vai pro fim).
        mockMvc.perform(post("/api/viagens/proposals/" + id + "/itinerary").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"dayDate\":\"2026-12-03\",\"title\":\"Dia 3\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/viagens/proposals/" + id + "/itinerary").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Dia em aberto\"}")).andExpect(status().isCreated());
        mockMvc.perform(post("/api/viagens/proposals/" + id + "/itinerary").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"dayDate\":\"2026-12-01\",\"title\":\"Dia 1\"}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/viagens/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itinerary.length()").value(3))
            .andExpect(jsonPath("$.itinerary[0].title").value("Dia 1"))
            .andExpect(jsonPath("$.itinerary[1].title").value("Dia 3"))
            .andExpect(jsonPath("$.itinerary[2].title").value("Dia em aberto"))
            .andExpect(jsonPath("$.totalCents").value(500000));
    }

    @Test
    @DisplayName("ITINERÁRIO: reorder re-materializa day_number 1..N na ordem recebida")
    void itineraryReorderViaHttp() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        UUID a = jdbcTemplate.queryForObject(
            "insert into travel_itinerary_days (company_id, proposal_id, day_number, title) "
                + "select company_id, ?, 1, 'A' from travel_proposals where id = ? returning id",
            UUID.class, id, id);
        UUID b = jdbcTemplate.queryForObject(
            "insert into travel_itinerary_days (company_id, proposal_id, day_number, title) "
                + "select company_id, ?, 2, 'B' from travel_proposals where id = ? returning id",
            UUID.class, id, id);
        UUID c = jdbcTemplate.queryForObject(
            "insert into travel_itinerary_days (company_id, proposal_id, day_number, title) "
                + "select company_id, ?, 3, 'C' from travel_proposals where id = ? returning id",
            UUID.class, id, id);

        // nova ordem C, A, B.
        mockMvc.perform(patch("/api/viagens/proposals/" + id + "/itinerary/reorder").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"orderedIds\":[\"" + c + "\",\"" + a + "\",\"" + b + "\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.itinerary[0].title").value("C"))
            .andExpect(jsonPath("$.itinerary[0].dayNumber").value(1))
            .andExpect(jsonPath("$.itinerary[1].title").value("A"))
            .andExpect(jsonPath("$.itinerary[2].title").value("B"));
    }

    @Test
    @DisplayName("ITINERÁRIO: PATCH dia inexistente → 404 itinerary_day_not_found")
    void itineraryDayNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(patch("/api/viagens/proposals/" + id + "/itinerary/" + UUID.randomUUID())
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"title\":\"Novo\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("itinerary_day_not_found"));
    }

    @Test
    @DisplayName("ITINERÁRIO: dayDate inválido → 400 invalid_date")
    void itineraryInvalidDate() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/viagens/proposals/" + id + "/itinerary").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"dayDate\":\"nao-eh-data\",\"title\":\"Dia 1\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_date"));
    }

    @Test
    @DisplayName("ITINERÁRIO: DELETE dia → 204; some do detalhe")
    void itineraryDelete() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        UUID id = openProposalId(t);

        mockMvc.perform(post("/api/viagens/proposals/" + id + "/itinerary").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"dayDate\":\"2026-12-01\",\"title\":\"Dia 1\"}")).andExpect(status().isCreated());
        UUID dayId = jdbcTemplate.queryForObject(
            "select id from travel_itinerary_days where proposal_id = ?", UUID.class, id);

        mockMvc.perform(delete("/api/viagens/proposals/" + id + "/itinerary/" + dayId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/viagens/proposals/" + id).header("Authorization", "Bearer " + t))
            .andExpect(jsonPath("$.itinerary.length()").value(0));
    }

    @Test
    @DisplayName("GET list devolve as propostas do tenant")
    void listProposals() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "viagens@test.dev", "viagens");
        String t = mintValidToken("viagens@test.dev", sub);
        openProposalId(t);

        mockMvc.perform(get("/api/viagens/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "oficina@test.dev", "oficina");
        String t = mintValidToken("oficina@test.dev", sub);

        mockMvc.perform(get("/api/viagens/proposals").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
