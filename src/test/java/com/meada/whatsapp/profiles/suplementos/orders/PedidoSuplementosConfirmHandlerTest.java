package com.meada.whatsapp.profiles.suplementos.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProduct;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService;
import com.meada.whatsapp.profiles.suplementos.catalog.SupVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoSuplementosConfirmHandler (camada 8.24): parse da tag {@code <pedido_suplementos>} +
 * create, com a ⭐ ESCAPADA de estoque. Prova que: tag válida cria o pedido, decrementa o estoque, usa
 * o unit_price da VARIANTE e DESCARTA o total da IA; OUT OF STOCK (qtd > estoque) → empty + 0 pedidos
 * + estoque INTACTO; variante inexistente → empty; entrega sem endereço → empty; texto sem tag →
 * empty. Inclui o teste de "corrida" (última unidade). Análogo ao PedidoLingerieConfirmHandlerTest.
 */
class PedidoSuplementosConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private PedidoSuplementosConfirmHandler handler;
    @Autowired
    private SupProductService productService;

    private static final UUID COMPANY = UUID.fromString("c8240000-0000-0000-0000-000000000092");
    private static final UUID USER = UUID.fromString("d8240000-0000-0000-0000-000000000092");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'suplementos')",
            COMPANY, "Suplementos H", "suplementos-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sup-h.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990192", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        jdbcTemplate.update("insert into sup_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    private int stock(UUID variantId) {
        return jdbcTemplate.queryForObject(
            "select stock_quantity from sup_variants where id = ?", Integer.class, variantId);
    }

    @Test
    @DisplayName("tag válida → cria, decrementa estoque, total descarta o da IA, snapshot do label")
    void parseAndCreate_valid() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Chocolate", "900g", null, 14990, 5, null);

        String aiText = "Confirmado: 2 Whey Chocolate 900g.\n"
            + "<pedido_suplementos>{\"delivery_address\":\"Rua das Flores 10\",\"items\":[{\"variant_id\":\""
            + v.id() + "\",\"qtd\":2}],\"notes\":null,\"total_cents\":99999}</pedido_suplementos>";

        Optional<SupOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 14990; subtotal = 29980; total = 29980 + 700 = 30680. O 99999 é DESCARTADO.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(14990);
        assertThat(order.get().items().get(0).variantLabel()).isEqualTo("Chocolate 900g");
        assertThat(order.get().subtotalCents()).isEqualTo(29980);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(30680);
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().status()).isEqualTo("aguardando");
        assertThat(stock(v.id())).isEqualTo(3);   // 5 - 2.
    }

    @Test
    @DisplayName("⭐ OUT OF STOCK (qtd > estoque) → empty + 0 pedidos + estoque INTACTO")
    void parseAndCreate_outOfStock_aborts() {
        SupProduct p = productService.create(COMPANY, USER, "Creatina", null, "aminoacidos", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), null, "300g", null, 9990, 2, null);

        String aiText = "Confirmado!\n<pedido_suplementos>{\"delivery_address\":\"Rua Z 5\",\"items\":[{\"variant_id\":\""
            + v.id() + "\",\"qtd\":3}],\"notes\":null}</pedido_suplementos>";
        Optional<SupOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(2);   // intacto.
    }

    @Test
    @DisplayName("⭐ corrida da última unidade: 1º pedido zera o estoque; 2º → out_of_stock (empty)")
    void parseAndCreate_lastUnit_secondFails() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Baunilha", "900g", null, 14990, 1, null);

        String aiText = "Confirmado!\n<pedido_suplementos>{\"delivery_address\":\"Rua W 1\",\"items\":[{\"variant_id\":\""
            + v.id() + "\",\"qtd\":1}],\"notes\":null}</pedido_suplementos>";

        Optional<SupOrder> first = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(first).isPresent();
        assertThat(stock(v.id())).isZero();

        Optional<SupOrder> second = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(second).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isEqualTo(1L);
        assertThat(stock(v.id())).isZero();
    }

    @Test
    @DisplayName("variant_id inexistente → empty (pedido não criado)")
    void parseAndCreate_invalidVariant() {
        String aiText = "Confirmado!\n<pedido_suplementos>{\"delivery_address\":\"Rua X\",\"items\":[{\"variant_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"notes\":null}</pedido_suplementos>";
        Optional<SupOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("tag SEM delivery_address → empty (SÓ entrega — endereço obrigatório)")
    void parseAndCreate_noAddress() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), null, "900g", null, 14990, 5, null);
        String aiText = "Confirmado!\n<pedido_suplementos>{\"items\":[{\"variant_id\":\"" + v.id()
            + "\",\"qtd\":1}],\"notes\":null}</pedido_suplementos>";
        Optional<SupOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(5);   // estoque intacto.
    }

    @Test
    @DisplayName("texto sem tag → empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<SupOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nosso catálogo de suplementos?");
        assertThat(order).isEmpty();
    }
}
