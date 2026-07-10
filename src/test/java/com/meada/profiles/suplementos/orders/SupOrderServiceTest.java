package com.meada.profiles.suplementos.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.suplementos.catalog.SupProduct;
import com.meada.profiles.suplementos.catalog.SupProductService;
import com.meada.profiles.suplementos.catalog.SupVariant;
import com.meada.profiles.suplementos.orders.SupOrderRepository.AddressRequiredException;
import com.meada.profiles.suplementos.orders.SupOrderRepository.OutOfStockException;
import com.meada.profiles.suplementos.orders.SupOrderService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o SupOrderService (camada 8.24): o recálculo de preço (variante × qtd, total da IA
 * descartado), o snapshot (product_name + variant_label congelados), a ⭐ ESCAPADA de estoque
 * (decremento exato + OutOfStockException aborta TUDO mesmo com item anterior já decrementado),
 * address_required, e o gate de aceite (aguardando → em_preparo ACEITE notifica / aguardando →
 * recusado RECUSA notifica defensivamente com motivo SEM conteúdo de saúde), transição inválida 409,
 * fluxo feliz em_preparo→saiu_entrega→entregue notificando cada, cancelamento SILENCIOSO, e que
 * {@code aguardando} NÃO notifica na criação. Análogo ao LingerieOrderServiceTest.
 */
@Import(SupOrderServiceTest.TestConfig.class)
class SupOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SupOrderService service;
    @Autowired
    private SupProductService productService;
    @Autowired
    private SupOrderRepository orderRepository;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8240000-0000-0000-0000-000000000093");
    private static final UUID USER = UUID.fromString("d8240000-0000-0000-0000-000000000093");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'suplementos')",
            COMPANY, "Suplementos O", "suplementos-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sup-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990193", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        jdbcTemplate.update("insert into sup_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    /** Cria um pedido com uma variante de estoque 5 (helper p/ os testes de gate). */
    private SupOrder seedOrder() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Chocolate", "900g", null, 14990, 5, null);
        return service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(v.id(), 2)), null);
    }

    // ---- Preço + estoque -----------------------------------------------------

    @Test
    @DisplayName("preço = variante × qtd; total com fee; total da IA descartado; snapshot preservado após editar o produto")
    void price_andSnapshot() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Baunilha", "900g", null, 14990, 5, null);

        SupOrder order = service.create(COMPANY, conversationId, contactId, "Rua Y 2",
            List.of(new OrderLineInput(v.id(), 2)), null);

        // unit_price = 14990; subtotal = 14990*2 = 29980; total = 29980 + 700 = 30680.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(14990);
        assertThat(order.items().get(0).productName()).isEqualTo("Whey");
        assertThat(order.items().get(0).variantLabel()).isEqualTo("Baunilha 900g");
        assertThat(order.subtotalCents()).isEqualTo(29980);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);
        assertThat(order.totalCents()).isEqualTo(30680);
        assertThat(order.status()).isEqualTo("aguardando");
        // estoque decrementado de 5 para 3.
        assertThat(stock(v.id())).isEqualTo(3);

        // editar o preço/nome depois NÃO altera o snapshot do pedido.
        productService.updateVariant(COMPANY, USER, p.id(), v.id(), null, null, null, 99999, null, null, false, null, false);
        productService.update(COMPANY, USER, p.id(), "Whey Novo", null, null, null, null);
        SupOrder refetched = service.get(COMPANY, order.id()).orElseThrow();
        assertThat(refetched.items().get(0).unitPriceCents()).isEqualTo(14990);       // snapshot preservado.
        assertThat(refetched.items().get(0).productName()).isEqualTo("Whey");         // snapshot preservado.
    }

    @Test
    @DisplayName("⭐ create com qtd > estoque → OutOfStockException + ZERO pedidos (estoque intacto, rollback)")
    void create_outOfStock_throwsAndNoOrder() {
        SupProduct p = productService.create(COMPANY, USER, "Creatina", null, "aminoacidos", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), null, "300g", null, 9990, 1, null);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X 1",
                List.of(new OrderLineInput(v.id(), 2)), null))
            .isInstanceOf(OutOfStockException.class);

        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(1);   // intacto (rollback).
    }

    @Test
    @DisplayName("⭐ 2 itens, o 2º esgotado → aborta TUDO; o decremento do 1º também sofre rollback (sem pedido parcial)")
    void create_secondItemOutOfStock_rollsBackFirst() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v1 = productService.addVariant(COMPANY, USER, p.id(), "Chocolate", "900g", null, 14990, 5, null);
        SupVariant v2 = productService.addVariant(COMPANY, USER, p.id(), "Morango", "900g", null, 14990, 1, null);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X 1",
                List.of(new OrderLineInput(v1.id(), 1), new OrderLineInput(v2.id(), 3)), null))
            .isInstanceOf(OutOfStockException.class);

        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
        // o decremento do 1º item (v1) foi revertido — estoque intacto nas DUAS variantes.
        assertThat(stock(v1.id())).isEqualTo(5);
        assertThat(stock(v2.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("variante inativa → linha ignorada → sem linha válida → IllegalArgumentException (aborta)")
    void create_inactiveVariant_aborts() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Cacau", "900g", null, 14990, 5, null);
        productService.toggleVariant(COMPANY, USER, p.id(), v.id(), false);   // inativa.

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X 1",
                List.of(new OrderLineInput(v.id(), 1)), null))
            .isInstanceOf(IllegalArgumentException.class);
        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(5);   // não decrementou.
    }

    @Test
    @DisplayName("variante de OUTRA empresa → ignorada → aborta (sem linha válida)")
    void create_otherCompanyVariant_aborts() {
        UUID other = UUID.fromString("c8240000-0000-0000-0000-0000000000aa");
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'suplementos')",
            other, "Outra", "outra-sup-93");
        UUID otherProduct = jdbcTemplate.queryForObject(
            "insert into sup_products (company_id, name, category) values (?, 'X', 'proteinas') returning id",
            UUID.class, other);
        UUID otherVariant = jdbcTemplate.queryForObject(
            "insert into sup_variants (company_id, product_id, size_label, price_cents, stock_quantity) "
                + "values (?, ?, '900g', 1000, 9) returning id", UUID.class, other, otherProduct);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X 1",
                List.of(new OrderLineInput(otherVariant, 1)), null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("entrega SEM endereço → AddressRequiredException (422), nada criado")
    void create_noAddress_throws() {
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), null, "900g", null, 14990, 5, null);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, null,
                List.of(new OrderLineInput(v.id(), 1)), null))
            .isInstanceOf(AddressRequiredException.class);
        Long count = jdbcTemplate.queryForObject("select count(*) from sup_orders", Long.class);
        assertThat(count).isZero();
    }

    // ---- Gate de aceite ------------------------------------------------------

    @Test
    @DisplayName("pedido nasce 'aguardando' e NÃO dispara notificação na criação")
    void create_isAguardando_andSilent() {
        SupOrder order = seedOrder();
        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("aceite (aguardando → em_preparo) → status atualiza + notificação 'aceito'")
    void accept_notifies() {
        SupOrder order = seedOrder();
        SupOrder updated = service.updateStatus(COMPANY, order.id(), "em_preparo", null);
        assertThat(updated.status()).isEqualTo("em_preparo");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("aceito");
    }

    @Test
    @DisplayName("recusa com motivo → terminal + notificação defensiva contém o motivo, SEM conteúdo de saúde")
    void reject_withReason_notifiesDefensively() {
        SupOrder order = seedOrder();
        SupOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "produto em falta");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("produto em falta");

        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("Infelizmente não conseguimos aceitar");
        assertThat(text).contains("produto em falta");
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        SupOrder order = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("fluxo feliz em_preparo → saiu_entrega → entregue notifica cada transição")
    void happyFlow_notifiesEach() {
        SupOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);      // 1 (aceite).
        service.updateStatus(COMPANY, order.id(), "saiu_entrega", null);    // 2.
        SupOrder delivered = service.updateStatus(COMPANY, order.id(), "entregue", null);   // 3.

        assertThat(delivered.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(3);
        assertThat(fakeEvolution.sent().get(1).text()).contains("entrega");
        assertThat(fakeEvolution.sent().get(2).text()).contains("entregue");
    }

    @Test
    @DisplayName("cancelar (em_preparo → cancelado) → terminal, SILENCIOSO (cancelado não notifica)")
    void cancel_silent() {
        SupOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);   // 1 envio (aceite).
        fakeEvolution.reset();
        SupOrder cancelled = service.updateStatus(COMPANY, order.id(), "cancelado", null);
        assertThat(cancelled.status()).isEqualTo("cancelado");
        assertThat(fakeEvolution.sent()).isEmpty();   // cancelado é silencioso.
    }

    // -------------------------------------------------------------------------
    // Onda 1 do backlog (#3b frete grátis · #9 restock)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("frete grátis: subtotal >= piso → taxa zerada; abaixo do piso → taxa normal")
    void freeShippingThreshold() {
        jdbcTemplate.update("update sup_config set free_shipping_threshold_cents = 20000 where company_id = ?",
            COMPANY);
        SupProduct p = productService.create(COMPANY, USER, "Whey", null, "proteinas", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Baunilha", "900g", null, 14990, 10, null);

        // 2 × 149,90 = 299,80 >= 200,00 → frete grátis.
        SupOrder free = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(v.id(), 2)), null);
        assertThat(free.deliveryFeeCents()).isZero();
        assertThat(free.totalCents()).isEqualTo(29980);

        // 1 × 149,90 < 200,00 → taxa da config (700).
        SupOrder paid = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(v.id(), 1)), null);
        assertThat(paid.deliveryFeeCents()).isEqualTo(700);
        assertThat(paid.totalCents()).isEqualTo(14990 + 700);
    }

    @Test
    @DisplayName("restock ao cancelar: estoque devolvido, stock_returned=true, idempotente")
    void restockOnCancel_idempotent() {
        SupProduct p = productService.create(COMPANY, USER, "Creatina", null, "aminoacidos", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), null, "300g", null, 9990, 5, null);
        SupOrder order = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(v.id(), 2)), null);
        assertThat(stock(v.id())).isEqualTo(3);   // decrementado na criação.

        service.updateStatus(COMPANY, order.id(), "cancelado", null);
        assertThat(stock(v.id())).isEqualTo(5);   // devolvido.
        Boolean returned = jdbcTemplate.queryForObject(
            "select stock_returned from sup_orders where id = ?", Boolean.class, order.id());
        assertThat(returned).isTrue();

        // idempotência no nível do repositório (duplo-cancelamento não devolve 2x).
        orderRepository.updateStatus(COMPANY, order.id(), "cancelado", null);
        assertThat(stock(v.id())).isEqualTo(5);
    }

    @Test
    @DisplayName("restock ao recusar (aguardando → recusado) também devolve")
    void restockOnReject() {
        SupProduct p = productService.create(COMPANY, USER, "Pré", null, "pre_treino", null);
        SupVariant v = productService.addVariant(COMPANY, USER, p.id(), "Frutas", "300g", null, 8990, 4, null);
        SupOrder order = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(v.id(), 1)), null);
        assertThat(stock(v.id())).isEqualTo(3);

        service.updateStatus(COMPANY, order.id(), "recusado", "sem entregador");
        assertThat(stock(v.id())).isEqualTo(4);
    }

    private int stock(UUID variantId) {
        return jdbcTemplate.queryForObject(
            "select stock_quantity from sup_variants where id = ?", Integer.class, variantId);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-suplementos";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
