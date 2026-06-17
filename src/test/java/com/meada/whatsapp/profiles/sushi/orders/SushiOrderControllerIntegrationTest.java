package com.meada.whatsapp.profiles.sushi.orders;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos (camada 7.1): list+filter, detail, patch status (200 + 409).
 * Pedidos são semeados via psql (no fluxo real vêm da IA). EvolutionSender real falha o envio
 * (base-url dummy) mas a notificação é best-effort → não quebra a transição.
 */
class SushiOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private String token;

    private void seedSushiOrder() {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("sushi@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'sushi' where id = ?", companyId);
        token = mintValidToken("sushi@test.dev", sub);

        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990004", "Cliente Pedido");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);
        UUID menuItem = jdbcTemplate.queryForObject(
            "insert into sushi_menu_items (company_id, name, price_cents, category) "
                + "values (?, 'Filadélfia', 3200, 'hot_rolls') returning id", UUID.class, companyId);
        orderId = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 6400, 6400, 'Rua Y 2') returning id", UUID.class, companyId, conv, contact);
        jdbcTemplate.update("insert into sushi_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 2, 3200, 'Filadélfia')", orderId, menuItem);
    }

    @Test
    @DisplayName("GET lista (filtro status) + detalhe com items e contato")
    void listAndDetail() throws Exception {
        seedSushiOrder();
        mockMvc.perform(get("/api/sushi/orders?status=recebido").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("recebido"));

        mockMvc.perform(get("/api/sushi/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryAddress").value("Rua Y 2"))
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qtd").value(2));
    }

    @Test
    @DisplayName("PATCH status recebido → preparo → 200")
    void patchStatusValid() throws Exception {
        seedSushiOrder();
        mockMvc.perform(patch("/api/sushi/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"preparo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("preparo"));
    }

    @Test
    @DisplayName("PATCH status recebido → entregue (inválido) → 409 invalid_status_transition")
    void patchStatusInvalid() throws Exception {
        seedSushiOrder();
        mockMvc.perform(patch("/api/sushi/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
