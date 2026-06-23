package com.meada.whatsapp.profiles.comida.orders;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos do perfil comida (camada 8.4): list+filter, detail, e o GATE DE
 * ACEITE via PATCH status (ESCAPADA 1) — aceite (aguardando→em_preparo), recusa com motivo
 * (aguardando→recusado), 409 transição inválida, 400 status inválido, 403 perfil errado. Pedidos
 * são semeados via jdbcTemplate (no fluxo real vêm da IA). EvolutionSender real falha o envio
 * (base-url dummy) mas a notificação é best-effort → não quebra a transição. Clone do
 * SushiOrderControllerIntegrationTest + a ESCAPADA 1.
 */
class ComidaOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private String token;

    /** Semeia um tenant 'comida' + um pedido 'aguardando' (default da migration). */
    private void seedComidaOrder() {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("comida@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'comida' where id = ?", companyId);
        token = mintValidToken("comida@test.dev", sub);

        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, companyId, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, companyId, "+5511999990016", "Cliente Pedido");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, companyId, contact, instance);
        UUID menuItem = jdbcTemplate.queryForObject(
            "insert into comida_menu_items (company_id, name, price_cents, category) "
                + "values (?, 'X-Burger', 2500, 'lanches') returning id", UUID.class, companyId);
        // pedido nasce 'aguardando' (default da coluna status) — não setar explicitamente.
        orderId = jdbcTemplate.queryForObject(
            "insert into comida_orders (company_id, conversation_id, contact_id, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, 5000, 5700, 'Rua Y 2') returning id", UUID.class, companyId, conv, contact);
        jdbcTemplate.update("insert into comida_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot) "
            + "values (?, ?, 2, 2500, 'X-Burger')", orderId, menuItem);
    }

    @Test
    @DisplayName("GET lista (filtro status=aguardando) + detalhe com items e contato")
    void listAndDetail() throws Exception {
        seedComidaOrder();
        mockMvc.perform(get("/api/comida/orders?status=aguardando").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("aguardando"));

        mockMvc.perform(get("/api/comida/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deliveryAddress").value("Rua Y 2"))
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qtd").value(2));
    }

    @Test
    @DisplayName("aceite: PATCH status aguardando → em_preparo → 200 (ESCAPADA 1)")
    void patchStatusAccept() throws Exception {
        seedComidaOrder();
        mockMvc.perform(patch("/api/comida/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"em_preparo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("em_preparo"));
    }

    @Test
    @DisplayName("recusa: PATCH status aguardando → recusado com motivo → 200 + rejectionReason gravado (ESCAPADA 1)")
    void patchStatusReject() throws Exception {
        seedComidaOrder();
        mockMvc.perform(patch("/api/comida/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"recusado\",\"rejectionReason\":\"fora da área de entrega\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("recusado"))
            .andExpect(jsonPath("$.rejectionReason").value("fora da área de entrega"));
    }

    @Test
    @DisplayName("PATCH status aguardando → entregue (inválido) → 409 invalid_status_transition")
    void patchStatusInvalidTransition() throws Exception {
        seedComidaOrder();
        mockMvc.perform(patch("/api/comida/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH status inexistente → 400 invalid_status")
    void patchStatusInvalidValue() throws Exception {
        seedComidaOrder();
        mockMvc.perform(patch("/api/comida/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"voando\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("tenant NÃO-comida (legal) batendo no /api/comida/orders → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID otherCompany = seedTenantAdmin("legal@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'legal' where id = ?", otherCompany);
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/comida/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
