package com.meada.whatsapp.profiles.padaria.menu;

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
 * Testa os endpoints de cardápio do perfil padaria (camada 8.8 / perfil padaria): CRUD de item (incl.
 * made_to_order/lead_time da ESCAPADA 1) + CRUD de OPÇÕES (ESCAPADA 2) + profile guard 403 pra tenant
 * não-padaria + 400/404. Clone do FloriculturaCatalogControllerIntegrationTest (catalog→menu).
 */
class PadariaMenuControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    /** Provisiona um tenant do perfil dado e devolve (companyId, token). */
    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD item sob encomenda: POST cria (made_to_order+lead) → GET lista → PATCH preço → toggle → DELETE")
    void crudItem() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "padaria@test.dev", "padaria");
        String t = mintValidToken("padaria@test.dev", sub);

        // POST (bolo sob encomenda).
        mockMvc.perform(post("/api/padaria/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Bolo de Chocolate\",\"description\":\"fofinho\",\"priceCents\":8000,"
                    + "\"category\":\"bolos_encomenda\",\"madeToOrder\":true,\"leadTimeDays\":3,\"allergens\":\"Glúten, leite\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Bolo de Chocolate"))
            .andExpect(jsonPath("$.madeToOrder").value(true))
            .andExpect(jsonPath("$.leadTimeDays").value(3))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from padaria_menu_items where name = 'Bolo de Chocolate'", UUID.class);

        // GET lista
        mockMvc.perform(get("/api/padaria/menu").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // PATCH preço
        mockMvc.perform(patch("/api/padaria/menu/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":8500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(8500));

        // toggle desliga
        mockMvc.perform(patch("/api/padaria/menu/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE
        mockMvc.perform(delete("/api/padaria/menu/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD opção (ESCAPADA 2): POST opção → GET item traz options → PATCH delta → toggle → DELETE")
    void crudOption() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "padaria@test.dev", "padaria");
        String t = mintValidToken("padaria@test.dev", sub);

        // item base
        mockMvc.perform(post("/api/padaria/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Bolo Festa\",\"priceCents\":12000,\"category\":\"bolos_encomenda\",\"madeToOrder\":true}"))
            .andExpect(status().isCreated());
        UUID itemId = jdbcTemplate.queryForObject("select id from padaria_menu_items where name = 'Bolo Festa'", UUID.class);

        // POST opção
        mockMvc.perform(post("/api/padaria/menu/" + itemId + "/options").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"groupLabel\":\"Recheio\",\"optionLabel\":\"Brigadeiro\",\"priceDeltaCents\":500,\"sortOrder\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.optionLabel").value("Brigadeiro"))
            .andExpect(jsonPath("$.priceDeltaCents").value(500));

        UUID optId = jdbcTemplate.queryForObject(
            "select id from padaria_menu_item_options where option_label = 'Brigadeiro'", UUID.class);

        // GET do item já traz a opção embutida (hidratação)
        mockMvc.perform(get("/api/padaria/menu/" + itemId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1))
            .andExpect(jsonPath("$.options[0].optionLabel").value("Brigadeiro"));

        // GET lista de opções do item (envelope "options")
        mockMvc.perform(get("/api/padaria/menu/" + itemId + "/options").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.options.length()").value(1));

        // PATCH delta da opção
        mockMvc.perform(patch("/api/padaria/menu/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceDeltaCents\":600}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceDeltaCents").value(600));

        // toggle opção
        mockMvc.perform(patch("/api/padaria/menu/" + itemId + "/options/" + optId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE opção
        mockMvc.perform(delete("/api/padaria/menu/" + itemId + "/options/" + optId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "padaria@test.dev", "padaria");
        String t = mintValidToken("padaria@test.dev", sub);
        mockMvc.perform(post("/api/padaria/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":100,\"category\":\"hot_rolls\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-padaria (legal) batendo no /api/padaria/menu → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/padaria/menu").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/padaria/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":1,\"category\":\"paes\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 menu_item_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "padaria@test.dev", "padaria");
        String t = mintValidToken("padaria@test.dev", sub);
        mockMvc.perform(get("/api/padaria/menu/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("menu_item_not_found"));
    }
}
