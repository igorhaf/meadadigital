package com.meada.whatsapp.profiles.suplementos.catalog;

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
 * Testa os endpoints de catálogo do perfil suplementos (camada 8.24): CRUD de produto + CRUD de
 * VARIANTES (⭐ a grade sabor×peso) + profile guard 403 pra tenant não-suplementos + 400/404/409.
 * Análogo ao LingerieProductControllerIntegrationTest, adaptado ao chassi de varejo de suplementos.
 */
class SupProductControllerIntegrationTest extends AbstractAdminIntegrationTest {

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
        seedTenant(sub, "suplementos@test.dev", "suplementos");
        String t = mintValidToken("suplementos@test.dev", sub);

        mockMvc.perform(post("/api/suplementos/products").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Whey Protein\",\"brand\":\"Growth\",\"category\":\"proteinas\",\"description\":\"concentrado\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Whey Protein"))
            .andExpect(jsonPath("$.active").value(true));

        UUID id = jdbcTemplate.queryForObject("select id from sup_products where name = 'Whey Protein'", UUID.class);

        mockMvc.perform(get("/api/suplementos/products").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/suplementos/products/" + id).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"brand\":\"Integral Medica\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brand").value("Integral Medica"));

        mockMvc.perform(patch("/api/suplementos/products/" + id + "/toggle").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/suplementos/products/" + id).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("CRUD variante: POST variante → GET produto traz variants → PATCH estoque → toggle → DELETE")
    void crudVariant() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "suplementos@test.dev", "suplementos");
        String t = mintValidToken("suplementos@test.dev", sub);

        mockMvc.perform(post("/api/suplementos/products").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"name\":\"Whey\",\"category\":\"proteinas\"}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from sup_products where name = 'Whey'", UUID.class);

        mockMvc.perform(post("/api/suplementos/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"flavor\":\"Chocolate\",\"sizeLabel\":\"900g\",\"sku\":\"SKU-1\",\"priceCents\":14990,\"stockQuantity\":5}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.flavor").value("Chocolate"))
            .andExpect(jsonPath("$.sizeLabel").value("900g"))
            .andExpect(jsonPath("$.stockQuantity").value(5));

        UUID variantId = jdbcTemplate.queryForObject(
            "select id from sup_variants where flavor = 'Chocolate' and size_label = '900g'", UUID.class);

        mockMvc.perform(get("/api/suplementos/products/" + productId).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(1))
            .andExpect(jsonPath("$.variants[0].sizeLabel").value("900g"));

        mockMvc.perform(get("/api/suplementos/products/" + productId + "/variants").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants.length()").value(1));

        mockMvc.perform(patch("/api/suplementos/products/" + productId + "/variants/" + variantId).header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"stockQuantity\":12}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockQuantity").value(12));

        mockMvc.perform(patch("/api/suplementos/products/" + productId + "/variants/" + variantId + "/toggle")
                .header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"active\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/suplementos/products/" + productId + "/variants/" + variantId).header("Authorization", "Bearer " + t))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST variante com SKU duplicado → 409 duplicate_variant")
    void duplicateVariant() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "suplementos@test.dev", "suplementos");
        String t = mintValidToken("suplementos@test.dev", sub);

        mockMvc.perform(post("/api/suplementos/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"Creatina\",\"category\":\"aminoacidos\"}"))
            .andExpect(status().isCreated());
        UUID productId = jdbcTemplate.queryForObject("select id from sup_products where name = 'Creatina'", UUID.class);

        mockMvc.perform(post("/api/suplementos/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"sizeLabel\":\"300g\",\"sku\":\"SKU-CR\",\"priceCents\":9990,\"stockQuantity\":3}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/suplementos/products/" + productId + "/variants").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"sizeLabel\":\"150g\",\"sku\":\"SKU-CR\",\"priceCents\":5990,\"stockQuantity\":9}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_variant"));
    }

    @Test
    @DisplayName("POST produto com categoria inválida → 400 invalid_category")
    void invalidCategory() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "suplementos@test.dev", "suplementos");
        String t = mintValidToken("suplementos@test.dev", sub);
        mockMvc.perform(post("/api/suplementos/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"category\":\"anabolizante\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_category"));
    }

    @Test
    @DisplayName("tenant NÃO-suplementos (legal) batendo no /api/suplementos/products → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "legal@test.dev", "legal");
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/suplementos/products").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
        mockMvc.perform(post("/api/suplementos/products").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"name\":\"X\",\"category\":\"proteinas\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("detalhe inexistente → 404 product_not_found")
    void detailNotFound() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "suplementos@test.dev", "suplementos");
        String t = mintValidToken("suplementos@test.dev", sub);
        mockMvc.perform(get("/api/suplementos/products/" + UUID.randomUUID()).header("Authorization", "Bearer " + t))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("product_not_found"));
    }
}
