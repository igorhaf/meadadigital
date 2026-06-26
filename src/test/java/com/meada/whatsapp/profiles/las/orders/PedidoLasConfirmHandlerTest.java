package com.meada.whatsapp.profiles.las.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.las.catalog.LasProduct;
import com.meada.whatsapp.profiles.las.catalog.LasProductService;
import com.meada.whatsapp.profiles.las.catalog.LasVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o PedidoLasConfirmHandler (camada 8.23): parse da tag {@code <pedido_las>} + create, com a
 * ESCAPADA de estoque E a ⭐ ESCAPADA do MESMO LOTE (same_lot_guaranteed). Prova que: tag válida cria o
 * pedido, decrementa o estoque, usa unit_price da VARIANTE (e do base do produto quando o priceCents
 * da variante é null), e DESCARTA o total da IA; OUT OF STOCK → empty + 0 pedidos + estoque intacto;
 * variante inexistente → empty; retirada sem endereço OK; entrega sem endereço → empty; e a regra do
 * mesmo lote: same_lot_guaranteed=true com DOIS lotes da MESMA cor → empty (mixed_dye_lots, 0 pedidos,
 * estoque intacto); same_lot_guaranteed=true com UM lote por cor → OK; same_lot_guaranteed=false com
 * lotes misturados → OK (sem checagem). Clone do PedidoLingerieConfirmHandlerTest, eixo size→dye_lot.
 */
class PedidoLasConfirmHandlerTest extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private PedidoLasConfirmHandler handler;
    @Autowired
    private LasProductService productService;

    private static final UUID COMPANY = UUID.fromString("c8230000-0000-0000-0000-000000000092");
    private static final UUID USER = UUID.fromString("d8230000-0000-0000-0000-000000000092");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'las')",
            COMPANY, "Las H", "las-h");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@las-h.dev', 'admin')",
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
        // taxa de entrega configurada (entra no total só em entrega).
        jdbcTemplate.update("insert into las_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    private int stock(UUID variantId) {
        return jdbcTemplate.queryForObject(
            "select stock_qty from las_variants where id = ?", Integer.class, variantId);
    }

    @Test
    @DisplayName("tag válida (variante com preço próprio) → cria, decrementa estoque, total descarta o da IA")
    void parseAndCreate_variantPrice() {
        LasProduct p = productService.create(COMPANY, USER, "Lã Merino", null, "las", 1990);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, 2190, 5);

        String aiText = "Confirmado: 2 novelos Lã Merino (Azul / lote L2024-A).\n"
            + "<pedido_las>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":2}],"
            + "\"fulfillment\":\"entrega\",\"same_lot_guaranteed\":false,\"endereco\":\"Rua das Flores 10\","
            + "\"total_cents\":99999}</pedido_las>";

        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        // unit_price = 2190 (preço da variante); subtotal = 2190*2 = 4380; total = 4380 + 700 = 5080. O 99999 é DESCARTADO.
        assertThat(order.get().items()).hasSize(1);
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(2190);
        assertThat(order.get().items().get(0).color()).isEqualTo("Azul");
        assertThat(order.get().items().get(0).dyeLot()).isEqualTo("L2024-A");
        assertThat(order.get().subtotalCents()).isEqualTo(4380);
        assertThat(order.get().deliveryFeeCents()).isEqualTo(700);
        assertThat(order.get().totalCents()).isEqualTo(5080);
        assertThat(order.get().fulfillment()).isEqualTo("entrega");
        assertThat(order.get().deliveryAddress()).isEqualTo("Rua das Flores 10");
        assertThat(order.get().status()).isEqualTo("aguardando");   // nasce aguardando (gate de aceite).
        // estoque decrementado de 5 para 3.
        assertThat(stock(v.id())).isEqualTo(3);
    }

    @Test
    @DisplayName("variante com priceCents null → unit_price vem do base_price do produto")
    void parseAndCreate_inheritsBasePrice() {
        LasProduct p = productService.create(COMPANY, USER, "Linha Crochê", null, "linhas", 1290);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Branco", "L2024-A", null, null, 10);

        String aiText = "Beleza!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":3}],"
            + "\"fulfillment\":\"entrega\",\"endereco\":\"Rua Y 20\",\"total_cents\":0}</pedido_las>";

        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().items().get(0).unitPriceCents()).isEqualTo(1290);   // herdou o base.
        // subtotal = 1290*3 = 3870; total = 3870 + 700 = 4570.
        assertThat(order.get().subtotalCents()).isEqualTo(3870);
        assertThat(order.get().totalCents()).isEqualTo(4570);
        assertThat(stock(v.id())).isEqualTo(7);   // 10 - 3.
    }

    @Test
    @DisplayName("OUT OF STOCK (qtd > estoque) → Optional.empty + 0 pedidos + estoque INTACTO")
    void parseAndCreate_outOfStock_aborts() {
        LasProduct p = productService.create(COMPANY, USER, "Lã", null, "las", 2000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Nude", "L2024-A", null, null, 2);

        // pede 3, só há 2 em estoque → out_of_stock → aborta tudo.
        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":3}],"
            + "\"fulfillment\":\"retirada\",\"total_cents\":6000}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from las_orders", Long.class);
        assertThat(count).isZero();
        // estoque NÃO foi alterado (o decremento condicional não afetou linha → rollback).
        assertThat(stock(v.id())).isEqualTo(2);
    }

    @Test
    @DisplayName("corrida da última unidade: 1º pedido zera o estoque; 2º pedido → out_of_stock (empty)")
    void parseAndCreate_lastUnit_secondFails() {
        LasProduct p = productService.create(COMPANY, USER, "Lã", null, "las", 2000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Vinho", "L2024-A", null, null, 1);

        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + v.id() + "\",\"qtd\":1}],"
            + "\"fulfillment\":\"retirada\",\"total_cents\":2000}</pedido_las>";

        // 1º pedido pega a última unidade — sucesso, estoque vai a 0.
        Optional<LasOrder> first = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(first).isPresent();
        assertThat(stock(v.id())).isZero();

        // 2º pedido pra mesma variante → estoque 0 < 1 → out_of_stock → empty.
        Optional<LasOrder> second = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(second).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from las_orders", Long.class);
        assertThat(count).isEqualTo(1L);   // só o primeiro.
        assertThat(stock(v.id())).isZero();
    }

    @Test
    @DisplayName("variant_id inexistente na tag → Optional.empty (pedido não criado)")
    void parseAndCreate_invalidVariant() {
        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\""
            + UUID.randomUUID() + "\",\"qtd\":1}],\"fulfillment\":\"retirada\",\"total_cents\":1000}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from las_orders", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("retirada sem endereço → OK (cria, sem taxa de entrega, sem deliveryAddress)")
    void parseAndCreate_retiradaNoAddress() {
        LasProduct p = productService.create(COMPANY, USER, "Kit", null, "kits", 9000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, null, 4);

        String aiText = "Beleza, retirada na loja!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + v.id()
            + "\",\"qtd\":1}],\"fulfillment\":\"retirada\",\"total_cents\":9000}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().fulfillment()).isEqualTo("retirada");
        assertThat(order.get().deliveryFeeCents()).isZero();   // retirada não soma taxa.
        assertThat(order.get().deliveryAddress()).isNull();
        assertThat(order.get().totalCents()).isEqualTo(9000);
    }

    @Test
    @DisplayName("entrega SEM endereço → Optional.empty (pedido não criado)")
    void parseAndCreate_entregaNoAddress() {
        LasProduct p = productService.create(COMPANY, USER, "Agulha", null, "agulhas", 1990);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Nº 4", "—", null, null, 5);
        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + v.id()
            + "\",\"qtd\":1}],\"fulfillment\":\"entrega\",\"total_cents\":1990}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from las_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(5);   // estoque intacto.
    }

    @Test
    @DisplayName("⭐ same_lot_guaranteed=true com DOIS lotes da MESMA cor → empty (mixed_dye_lots), 0 pedidos, estoque intacto")
    void parseAndCreate_sameLotGuaranteed_mixedLots_aborts() {
        LasProduct p = productService.create(COMPANY, USER, "Lã Merino", null, "las", 1990);
        // Duas variantes da MESMA cor (Azul), lotes DIFERENTES.
        LasVariant loteA = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, null, 10);
        LasVariant loteB = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-B", null, null, 10);

        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + loteA.id()
            + "\",\"qtd\":3},{\"variant_id\":\"" + loteB.id() + "\",\"qtd\":2}],"
            + "\"fulfillment\":\"retirada\",\"same_lot_guaranteed\":true,\"total_cents\":9950}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from las_orders", Long.class);
        assertThat(count).isZero();
        // estoque devolvido pelo rollback — ambos intactos.
        assertThat(stock(loteA.id())).isEqualTo(10);
        assertThat(stock(loteB.id())).isEqualTo(10);
    }

    @Test
    @DisplayName("⭐ same_lot_guaranteed=true com UM lote por cor → OK (cria, decrementa)")
    void parseAndCreate_sameLotGuaranteed_oneLotPerColor_ok() {
        LasProduct p = productService.create(COMPANY, USER, "Lã Merino", null, "las", 2000);
        // Cores DIFERENTES, cada uma de um único lote — não há cor com 2 lotes.
        LasVariant azul = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, null, 10);
        LasVariant rosa = productService.addVariant(COMPANY, USER, p.id(), "Rosa", "L2024-B", null, null, 10);

        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + azul.id()
            + "\",\"qtd\":3},{\"variant_id\":\"" + rosa.id() + "\",\"qtd\":2}],"
            + "\"fulfillment\":\"retirada\",\"same_lot_guaranteed\":true,\"total_cents\":0}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().sameLotGuaranteed()).isTrue();
        assertThat(order.get().items()).hasSize(2);
        // subtotal = 2000*3 + 2000*2 = 10000.
        assertThat(order.get().subtotalCents()).isEqualTo(10000);
        assertThat(stock(azul.id())).isEqualTo(7);
        assertThat(stock(rosa.id())).isEqualTo(8);
    }

    @Test
    @DisplayName("⭐ same_lot_guaranteed=false com lotes misturados da mesma cor → OK (sem checagem)")
    void parseAndCreate_sameLotFalse_mixedLots_ok() {
        LasProduct p = productService.create(COMPANY, USER, "Lã Merino", null, "las", 2000);
        LasVariant loteA = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, null, 10);
        LasVariant loteB = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-B", null, null, 10);

        // same_lot_guaranteed=false → mistura de lotes é permitida.
        String aiText = "Confirmado!\n<pedido_las>{\"items\":[{\"variant_id\":\"" + loteA.id()
            + "\",\"qtd\":3},{\"variant_id\":\"" + loteB.id() + "\",\"qtd\":2}],"
            + "\"fulfillment\":\"retirada\",\"same_lot_guaranteed\":false,\"total_cents\":0}</pedido_las>";
        Optional<LasOrder> order = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(order).isPresent();
        assertThat(order.get().sameLotGuaranteed()).isFalse();
        assertThat(order.get().items()).hasSize(2);
        assertThat(stock(loteA.id())).isEqualTo(7);
        assertThat(stock(loteB.id())).isEqualTo(8);
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<LasOrder> order = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer ver nossas lãs?");
        assertThat(order).isEmpty();
    }
}
