package com.meada.whatsapp.profiles.padaria.orders;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de pedidos do perfil padaria (camada 8.8 / perfil padaria): list+filter, detail,
 * e o GATE DE ACEITE via PATCH status — aceite (aguardando→em_preparo), recusa com motivo
 * (aguardando→recusado), o funil que diverge (pronto→retirado / pronto→saiu_entrega), 409 transição
 * inválida, 400 status inválido, 403 perfil errado. Pedidos são semeados via jdbcTemplate (no fluxo
 * real vêm da IA). EvolutionSender real falha o envio (base-url dummy) mas a notificação é best-effort
 * → não quebra a transição. Clone do FloriculturaOrderControllerIntegrationTest.
 */
class PadariaOrderControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID companyId;
    private UUID orderId;
    private String token;

    /** Semeia um tenant 'padaria' + um pedido 'aguardando' (default da migration). */
    private void seedPadariaOrder(String fulfillment) {
        UUID sub = UUID.randomUUID();
        companyId = seedTenantAdmin("padaria@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'padaria' where id = ?", companyId);
        token = mintValidToken("padaria@test.dev", sub);

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
            "insert into padaria_menu_items (company_id, name, price_cents, category, made_to_order) "
                + "values (?, 'Pão Francês', 100, 'paes', false) returning id", UUID.class, companyId);
        // pedido nasce 'aguardando' (default da coluna status) — não setar explicitamente.
        String addr = "entrega".equals(fulfillment) ? "'Rua Y 2'" : "null";
        orderId = jdbcTemplate.queryForObject(
            "insert into padaria_orders (company_id, conversation_id, contact_id, fulfillment, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, '" + fulfillment + "', 500, 500, " + addr + ") returning id",
            UUID.class, companyId, conv, contact);
        jdbcTemplate.update("insert into padaria_order_items (order_id, menu_item_id, qtd, unit_price_cents, item_name_snapshot, made_to_order_snapshot) "
            + "values (?, ?, 5, 100, 'Pão Francês', false)", orderId, menuItem);
    }

    @Test
    @DisplayName("GET lista (filtro status=aguardando) + detalhe com items e contato")
    void listAndDetail() throws Exception {
        seedPadariaOrder("retirada");
        mockMvc.perform(get("/api/padaria/orders?status=aguardando").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].status").value("aguardando"))
            .andExpect(jsonPath("$.items[0].fulfillment").value("retirada"));

        mockMvc.perform(get("/api/padaria/orders/" + orderId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactName").value("Cliente Pedido"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].qtd").value(5));
    }

    @Test
    @DisplayName("retirada: aguardando → em_preparo → pronto → retirado (200 em cada)")
    void retiradaFlow() throws Exception {
        seedPadariaOrder("retirada");
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"em_preparo\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("em_preparo"));
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"pronto\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pronto"));
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"retirado\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("retirado"));
    }

    @Test
    @DisplayName("entrega: pronto → saiu_entrega → entregue (200 em cada)")
    void entregaFlow() throws Exception {
        seedPadariaOrder("entrega");
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"em_preparo\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"pronto\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"saiu_entrega\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("saiu_entrega"));
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("entregue"));
    }

    @Test
    @DisplayName("recusa: aguardando → recusado com motivo → 200 + rejectionReason gravado")
    void patchStatusReject() throws Exception {
        seedPadariaOrder("retirada");
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"recusado\",\"rejectionReason\":\"sem fermento hoje\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("recusado"))
            .andExpect(jsonPath("$.rejectionReason").value("sem fermento hoje"));
    }

    @Test
    @DisplayName("PATCH status aguardando → entregue (inválido) → 409 invalid_status_transition")
    void patchStatusInvalidTransition() throws Exception {
        seedPadariaOrder("retirada");
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"entregue\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }

    @Test
    @DisplayName("PATCH status inexistente → 400 invalid_status")
    void patchStatusInvalidValue() throws Exception {
        seedPadariaOrder("retirada");
        mockMvc.perform(patch("/api/padaria/orders/" + orderId + "/status").header("Authorization", "Bearer " + token)
                .contentType(JSON).content("{\"newStatus\":\"voando\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("invalid_status"));
    }

    @Test
    @DisplayName("tenant NÃO-padaria (legal) batendo no /api/padaria/orders → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID otherCompany = seedTenantAdmin("legal@test.dev", sub);
        jdbcTemplate.update("update companies set profile_id = 'legal' where id = ?", otherCompany);
        String t = mintValidToken("legal@test.dev", sub);
        mockMvc.perform(get("/api/padaria/orders").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
