package com.meada.whatsapp.profiles.las.catalog;

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
 * Testa os endpoints de catálogo do perfil las (camada 8.23): CRUD de produto + CRUD de VARIANTES (⭐
 * a grade COR × DYE_LOT) + profile guard 403 pra tenant não-las + 400/404/409. Clone do
 * LingerieProductControllerIntegrationTest, eixo size→dye_lot — SEM teste de invalid_size (color/dye_lot
 * são texto livre).
 */
class LasProductControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    /** Provisiona um tenant do perfil dado e devolve o companyId. */
    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("CRUD produto: POST cria → GET lista → PATCH edita → toggle → DELETE")
    void crudProduct() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "las@test.dev", "las");
        String t = mintValidToken("las@test.dev", sub);

        mockMvc.perform(post("/api/las/products").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Lã Merino\",\"description\":\"100% lã\",\"category\":\"las\",\"basePriceCents\":1990}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Lã Merino"))
            .andExpect(jsonPath("$.available").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from las_products where name = 'Lã Merino'", UUID.class);

        mockMvc.perform(get("/api/las/products").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/las/products/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"basePriceCents\":2490}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.basePriceCents").value(2490));

        mockMvc.perform(patch("/api/las/products/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(delete("/api/las/products/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD variante: POST variante → GET produto traz variants → PATCH estoque → toggle → DELETE")
    void crudVariant() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "las@test.dev", "las");
        String t = mintValidToken("las@test.dev", sub);

        mockMvc.perform(post("/api/las/products").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Lã\",\"category\":\"las\",\"basePriceCents\":1990}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from las_products where name = 'Lã'", UUID.class);

        mockMvc.perform(post("/api/las/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"color\":\"Azul\",\"dyeLot\":\"L2024-A\",\"sku\":\"SKU-1\",\"priceCents\":2190,\"stockQty\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.color").value("Azul"))
            .andExpect(jsonPath("$.dyeLot").value("L2024-A"))
            .andExpect(jsonPath("$.stockQty").value(5));

        UUID variantId = jdbcTemplate.queryForObject(
            "select id from las_variants where color = 'Azul' and dye_lot = 'L2024-A'", UUID.class);

        // GET do produto já traz a variante embutida (hidratação).
        mockMvc.perform(get("/api/las/products/" + productId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(1))
            .andExpect(jsonPath("$.variants[0].color").value("Azul"))
            .andExpect(jsonPath("$.variants[0].dyeLot").value("L2024-A"));

        // GET lista de variantes do produto (envelope "variants").
        mockMvc.perform(get("/api/las/products/" + productId + "/variants").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(1));

        // PATCH estoque da variante.
        mockMvc.perform(patch("/api/las/products/" + productId + "/variants/" + variantId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"stockQty\":12}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQty").value(12));

        // toggle variante.
        mockMvc.perform(patch("/api/las/products/" + productId + "/variants/" + variantId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"available\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(false));

        // DELETE variante.
        mockMvc.perform(delete("/api/las/products/" + productId + "/variants/" + variantId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST variante duplicada (mesma cor × dye_lot) → 409 duplicate_variant")
    void duplicateVariant() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "las@test.dev", "las");
        String t = mintValidToken("las@test.dev", sub);

        mockMvc.perform(post("/api/las/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Linha\",\"category\":\"linhas\",\"basePriceCents\":1290}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from las_products where name = 'Linha'", UUID.class);

        mockMvc.perform(post("/api/las/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"color\":\"Branco\",\"dyeLot\":\"L2024-A\",\"stockQty\":3}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/las/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"color\":\"Branco\",\"dyeLot\":\"L2024-A\",\"stockQty\":9}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_variant"));
    }

    @Test
    @DisplayName("POST produto com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "las@test.dev", "las");
        String t = mintValidToken("las@test.dev", sub);
        mockMvc.perform(post("/api/las/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"category\":\"fralda\",\"basePriceCents\":100}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-las (legal) batendo no /api/las/products → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/las/products").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/las/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"category\":\"las\",\"basePriceCents\":1}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 product_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "las@test.dev", "las");
        String t = mintValidToken("las@test.dev", sub);
        mockMvc.perform(get("/api/las/products/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("product_not_found"));
    }
}
