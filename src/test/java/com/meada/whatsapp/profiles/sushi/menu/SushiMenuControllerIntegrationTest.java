package com.meada.whatsapp.profiles.sushi.menu;

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
 * Testa os endpoints de cardápio (camada 7.1): CRUD + profile guard 403 pra tenant não-sushi.
 */
class SushiMenuControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    /** Provisiona um tenant do perfil dado e devolve (companyId, token). */
    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crud() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);

        // POST
        mockMvc.perform(post("/api/sushi/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Hot Filadélfia\",\"description\":\"cream+salmão\",\"priceCents\":3200,\"category\":\"hot_rolls\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Hot Filadélfia"))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from sushi_menu_items where name = 'Hot Filadélfia'", UUID.class);

        // GET lista
        mockMvc.perform(get("/api/sushi/menu").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // PATCH preço
        mockMvc.perform(patch("/api/sushi/menu/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"priceCents\":3500}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(3500));

        // toggle desliga
        mockMvc.perform(patch("/api/sushi/menu/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE
        mockMvc.perform(delete("/api/sushi/menu/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(post("/api/sushi/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":100,\"category\":\"pizza\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("GET com filtro available=true esconde itens desligados")
    void filterAvailable() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        jdbcTemplate.update("insert into sushi_menu_items (company_id, name, price_cents, category, available) "
            + "values (?, 'On', 1000, 'bebidas', true), (?, 'Off', 1000, 'bebidas', false)", companyId, companyId);

        mockMvc.perform(get("/api/sushi/menu?available=true").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].name").value("On"));
    }

    @Test
    @DisplayName("tenant NÃO-sushi (legal) batendo no /api/sushi/menu → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/sushi/menu").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/sushi/menu").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"priceCents\":1,\"category\":\"bebidas\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 menu_item_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "sushi@test.dev", "sushi");
        String t = mintValidToken("sushi@test.dev", sub);
        mockMvc.perform(get("/api/sushi/menu/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("menu_item_not_found"));
    }
}
