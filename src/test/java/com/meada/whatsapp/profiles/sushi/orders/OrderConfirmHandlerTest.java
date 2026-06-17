package com.meada.whatsapp.profiles.sushi.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuItem;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o OrderConfirmHandler (camada 7.1): parse OK + create, item_id inválido → empty, sem tag → empty.
 */
class OrderConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private OrderConfirmHandler handler;
    @Autowired
    private SushiMenuService menuService;

    private static final UUID COMPANY = UUID.fromString("c6000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d6000000-0000-0000-0000-000000000001");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi H", "sushi-h");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no SushiMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990002", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega configurada (entra no total).
        jdbcTemplate.update("insert into sushi_restaurant_config (company_id, delivery_fee_cents) values (?, 800)", COMPANY);
    }

    @Test
    @DisplayName("tag <pedido> com itens válidos → cria pedido, total recalculado (subtotal + fee)")
    void parseAndCreate_ok() {
        SushiMenuItem fila = menuService.create(COMPANY, USER, "Filadélfia", null, 3200, "hot_rolls");
        SushiMenuItem cali = menuService.create(COMPANY, USER, "California", null, 2800, "hot_rolls");

        String aiText = "Confirmado: 2 Filadélfia + 1 California. Já já tá saindo!\n"
            + "<pedido>{\"items\":[{\"item_id\":\"" + fila.id() + "\",\"qtd\":2},"
            + "{\"item_id\":\"" + cali.id() + "\",\"qtd\":1}],\"endereco\":\"Rua das Flores 10\","
            + "\"total_cents\":99999}</pedido>";

        Optional<SushiOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // subtotal = 2*3200 + 1*2800 = 9200; total = 9200 + 800 (fee) = 10000. O total_cents
        // mentiroso (99999) do JSON é DESCARTADO.
        assertThat(order.get().subtotalCents()).isEqualTo(9200);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(800);
        assertThat(order.get().totalCents()).isEqualTo(10000);
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().items()).hasSize(2);
    }

    @Test
    @DisplayName("item_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<pedido>{\"items\":[{\"item_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"endereco\":\"Rua X\",\"total_cents\":1000}</pedido>";
        Optional<SushiOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from sushi_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<SushiOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso cardápio?");
        assertThat(order).isEmpty();
    }
}
