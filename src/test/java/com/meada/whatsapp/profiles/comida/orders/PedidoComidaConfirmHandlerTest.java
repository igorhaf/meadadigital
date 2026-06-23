package com.meada.whatsapp.profiles.comida.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuItem;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuOption;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoComidaConfirmHandler (camada 8.4): parse da tag {@code <pedido_comida>} + create,
 * com a ESCAPADA 2 (opções por item: unit_price = base + Σ deltas; option_id fantasma ABORTA). O
 * {@code total_cents} mentiroso da IA é sempre DESCARTADO. Clone do OrderConfirmHandlerTest (sushi)
 * + as opções.
 */
class PedidoComidaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoComidaConfirmHandler handler;
    @Autowired
    private ComidaMenuService menuService;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000072");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000072");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'comida')",
            COMPANY, "Comida H", "comida-h");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no ComidaMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@comida-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990072", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega configurada (entra no total).
        jdbcTemplate.update("insert into comida_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    @Test
    @DisplayName("tag com itens válidos COM opções → cria pedido, unit_price = base + Σ deltas, total descarta o da IA")
    void parseAndCreate_withOptions() {
        ComidaMenuItem burger = menuService.create(COMPANY, USER, "X-Burger", null, 2500, "lanches");
        ComidaMenuOption bacon = menuService.addOption(COMPANY, USER, burger.id(), "Adicionais", "Bacon", 300, 0);
        ComidaMenuOption queijo = menuService.addOption(COMPANY, USER, burger.id(), "Adicionais", "Queijo", 200, 1);

        String aiText = "Confirmado: 2 X-Burger com bacon e queijo. Vai pra confirmação do restaurante!\n"
            + "<pedido_comida>{\"items\":[{\"item_id\":\"" + burger.id() + "\",\"qtd\":2,"
            + "\"options\":[\"" + bacon.id() + "\",\"" + queijo.id() + "\"]}],"
            + "\"endereco\":\"Rua das Flores 10\",\"total_cents\":99999}</pedido_comida>";

        Optional<ComidaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 2500 (base) + 300 (bacon) + 200 (queijo) = 3000.
        // subtotal = 3000 * 2 = 6000; total = 6000 + 700 (fee) = 6700. O total_cents (99999) é DESCARTADO.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(3000);
        assertThat(order.get().items().get(0).options()).hasSize(2);
        assertThat(order.get().subtotalCents()).isEqualTo(6000);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(6700);
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().status()).isEqualTo("aguardando");   // nasce aguardando (ESCAPADA 1).
    }

    @Test
    @DisplayName("item sem opções → cria com unit_price = base")
    void parseAndCreate_noOptions() {
        ComidaMenuItem refri = menuService.create(COMPANY, USER, "Coca-Cola", null, 600, "bebidas");
        String aiText = "Beleza!\n<pedido_comida>{\"items\":[{\"item_id\":\"" + refri.id() + "\",\"qtd\":3}],"
            + "\"endereco\":\"Rua Y 20\",\"total_cents\":0}</pedido_comida>";

        Optional<ComidaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(600);
        assertThat(order.get().items().get(0).options()).isEmpty();
        // subtotal = 600*3 = 1800; total = 1800 + 700 = 2500.
        assertThat(order.get().subtotalCents()).isEqualTo(1800);
        assertThat(order.get().totalCents()).isEqualTo(2500);
    }

    @Test
    @DisplayName("item_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidItem() {
        String aiText = "Confirmado!\n<pedido_comida>{\"items\":[{\"item_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"endereco\":\"Rua X\",\"total_cents\":1000}</pedido_comida>";
        Optional<ComidaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from comida_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("option_id fantasma (não pertence ao item) → Optional.empty + 0 pedidos (ESCAPADA 2)")
    void parseAndCreate_invalidOption_aborts() {
        ComidaMenuItem burger = menuService.create(COMPANY, USER, "X-Tudo", null, 3000, "lanches");
        // opção VÁLIDA num OUTRO item — não pertence ao X-Tudo → o repo deve recusar.
        ComidaMenuItem outro = menuService.create(COMPANY, USER, "Pizza", null, 4000, "pizzas");
        ComidaMenuOption optDeOutroItem = menuService.addOption(COMPANY, USER, outro.id(), "Borda", "Cheddar", 500, 0);

        String aiText = "Confirmado!\n<pedido_comida>{\"items\":[{\"item_id\":\"" + burger.id() + "\",\"qtd\":1,"
            + "\"options\":[\"" + optDeOutroItem.id() + "\"]}],\"endereco\":\"Rua Z\",\"total_cents\":3500}</pedido_comida>";

        Optional<ComidaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from comida_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<ComidaOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso cardápio?");
        assertThat(order).isEmpty();
    }

    @Test
    @DisplayName("tag sem endereço → Optional.empty (pedido não criado)")
    void parseAndCreate_noAddress() {
        ComidaMenuItem item = menuService.create(COMPANY, USER, "Batata", null, 1500, "porcoes");
        String aiText = "Confirmado!\n<pedido_comida>{\"items\":[{\"item_id\":\"" + item.id()
            + "\",\"qtd\":1}],\"total_cents\":1500}</pedido_comida>";
        Optional<ComidaOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from comida_orders", Long.class);
        assertThat(count).isZero();
    }
}
