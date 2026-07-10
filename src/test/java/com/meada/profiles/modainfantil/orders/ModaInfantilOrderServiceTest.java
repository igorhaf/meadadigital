package com.meada.profiles.modainfantil.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.modainfantil.catalog.ModaInfantilProduct;
import com.meada.profiles.modainfantil.catalog.ModaInfantilProductService;
import com.meada.profiles.modainfantil.catalog.ModaInfantilVariant;
import com.meada.profiles.modainfantil.orders.ModaInfantilOrderRepository.OutOfStockException;
import com.meada.profiles.modainfantil.orders.ModaInfantilOrderService.InvalidStatusTransitionException;
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
 * Testa o ModaInfantilOrderService (camada 8.22): o recálculo de preço (variante/base × qtd, total da
 * IA descartado), o snapshot, a escapada de estoque (decremento + OutOfStockException aborta), o gate
 * de aceite, transição inválida 409, fluxo feliz separando→enviado→entregue, e — ⭐ a ADAPTAÇÃO 8.22 — o
 * RESTOCK ON CANCEL: recusar/cancelar DEVOLVE o estoque das variantes (e é idempotente: um 2º cancelar
 * não devolve de novo). Clone do LingerieOrderServiceTest + os testes de restock.
 */
@Import(ModaInfantilOrderServiceTest.TestConfig.class)
class ModaInfantilOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ModaInfantilOrderService service;
    @Autowired
    private ModaInfantilProductService productService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;
    @org.springframework.beans.factory.annotation.Autowired
    private com.meada.profiles.modainfantil.alerts.ModaInfantilStockAlertService alertService;

    private static final UUID COMPANY = UUID.fromString("c8220000-0000-0000-0000-000000000093");
    private static final UUID USER = UUID.fromString("d8220000-0000-0000-0000-000000000093");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'moda_infantil')",
            COMPANY, "Moda Infantil O", "moda-infantil-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@moda-infantil-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990293", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        jdbcTemplate.update("insert into moda_infantil_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    private int stock(UUID variantId) {
        return jdbcTemplate.queryForObject(
            "select stock_qty from moda_infantil_variants where id = ?", Integer.class, variantId);
    }

    private boolean stockReturned(UUID orderId) {
        return jdbcTemplate.queryForObject(
            "select stock_returned from moda_infantil_orders where id = ?", Boolean.class, orderId);
    }

    /** Cria um pedido de ENTREGA com uma variante de estoque 5 (helper p/ os testes de gate). */
    private record SeedResult(ModaInfantilOrder order, UUID variantId) {}

    private SeedResult seedOrder() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Conjunto", null, "menina", 5000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "2a", "Rosa", null, null, 5);
        ModaInfantilOrder order = service.create(COMPANY, conversationId, contactId, "entrega", "Rua X 1",
            List.of(new OrderLineInput(v.id(), 2)), null, null);
        return new SeedResult(order, v.id());
    }

    // ---- Preço + estoque -----------------------------------------------------

    @Test
    @DisplayName("preço = variante (ou base) × qtd; total com fee em entrega; snapshot preservado após editar o produto")
    void price_andSnapshot() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Conjunto", null, "menina", 5000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "2a", "Rosa", null, 8990, 5);

        ModaInfantilOrder order = service.create(COMPANY, conversationId, contactId, "entrega", "Rua Y 2",
            List.of(new OrderLineInput(v.id(), 2)), null, null);

        // unit_price = 8990 (variante); subtotal = 8990*2 = 17980; total = 17980 + 700 = 18680.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(8990);
        assertThat(order.subtotalCents()).isEqualTo(17980);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);
        assertThat(order.totalCents()).isEqualTo(18680);

        // editar o preço da variante DEPOIS não altera o snapshot do pedido.
        productService.updateVariant(COMPANY, USER, p.id(), v.id(), null, null, null, 99999, null, null, false);
        ModaInfantilOrder refetched = service.get(COMPANY, order.id()).orElseThrow();
        assertThat(refetched.items().get(0).unitPriceCents()).isEqualTo(8990);   // snapshot preservado.
    }

    @Test
    @DisplayName("create com qtd > estoque → OutOfStockException + ZERO pedidos (estoque intacto)")
    void create_outOfStock_throwsAndNoOrder() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Body", null, "bebe", 5000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "RN", "Cinza", null, null, 1);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(v.id(), 2)), null, null))
            .isInstanceOf(OutOfStockException.class);

        Long count = jdbcTemplate.queryForObject("select count(*) from moda_infantil_orders", Long.class);
        assertThat(count).isZero();
        assertThat(stock(v.id())).isEqualTo(1);   // intacto (rollback).
    }

    // ---- Gate de aceite ------------------------------------------------------

    @Test
    @DisplayName("pedido nasce 'aguardando' e NÃO dispara notificação na criação")
    void create_isAguardando_andSilent() {
        ModaInfantilOrder order = seedOrder().order();
        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(order.stockReturned()).isFalse();   // ainda não devolveu nada.
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("aceite (aguardando → separando) → status atualiza + notificação 'separando' (sem mexer no estoque)")
    void accept_notifies() {
        SeedResult seed = seedOrder();
        // estoque já foi decrementado de 5 → 3 na criação.
        assertThat(stock(seed.variantId())).isEqualTo(3);

        ModaInfantilOrder updated = service.updateStatus(COMPANY, seed.order().id(), "separando", null);
        assertThat(updated.status()).isEqualTo("separando");

        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("separando");
        // aceitar NÃO devolve estoque.
        assertThat(stock(seed.variantId())).isEqualTo(3);
        assertThat(stockReturned(seed.order().id())).isFalse();
    }

    @Test
    @DisplayName("⭐ recusa (aguardando → recusado) → DEVOLVE o estoque + stock_returned=true + notifica defensivamente com motivo")
    void reject_restocksAndNotifies() {
        SeedResult seed = seedOrder();
        assertThat(stock(seed.variantId())).isEqualTo(3);   // 5 - 2 na criação.

        ModaInfantilOrder rejected = service.updateStatus(COMPANY, seed.order().id(), "recusado", "produto sem estoque na cor");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("produto sem estoque na cor");

        // ⭐ estoque DEVOLVIDO: 3 + 2 = 5; flag marcado.
        assertThat(stock(seed.variantId())).isEqualTo(5);
        assertThat(rejected.stockReturned()).isTrue();
        assertThat(stockReturned(seed.order().id())).isTrue();

        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("Infelizmente não conseguimos aceitar");   // texto fixo defensivo.
        assertThat(text).contains("produto sem estoque na cor");             // motivo concatenado.
    }

    @Test
    @DisplayName("⭐ cancelamento (separando → cancelado) DEVOLVE o estoque; um 2º cancelar é IDEMPOTENTE (não devolve de novo)")
    void cancel_restocks_idempotent() {
        SeedResult seed = seedOrder();
        service.updateStatus(COMPANY, seed.order().id(), "separando", null);   // aceite (1 envio); estoque intacto em 3.
        assertThat(stock(seed.variantId())).isEqualTo(3);
        fakeEvolution.reset();

        // cancelar → devolve 2 → estoque 5; stock_returned=true.
        ModaInfantilOrder cancelled = service.updateStatus(COMPANY, seed.order().id(), "cancelado", null);
        assertThat(cancelled.status()).isEqualTo("cancelado");
        assertThat(stock(seed.variantId())).isEqualTo(5);
        assertThat(cancelled.stockReturned()).isTrue();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelado");

        // ⭐ IDEMPOTÊNCIA: forçar um 2º "cancelar" (transição de terminal é inválida, mas mesmo que o
        // banco fosse forçado, o restock não pode rodar 2x). Simulamos chamando o repositório direto
        // com o mesmo status — stock_returned já é true → NÃO devolve de novo.
        // (Via service, cancelado→cancelado é transição inválida; provamos a idempotência no nível do
        // repositório, que é onde o restock vive.)
        jdbcTemplate.update("update moda_infantil_orders set status = 'separando', stock_returned = true where id = ?",
            seed.order().id());
        // agora um cancelamento real (separando→cancelado) com stock_returned JÁ true → não devolve.
        service.updateStatus(COMPANY, seed.order().id(), "cancelado", null);
        assertThat(stock(seed.variantId())).isEqualTo(5);   // continua 5 — não devolveu 2x.
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado, estoque intacto")
    void invalidTransition() {
        SeedResult seed = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, seed.order().id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
        assertThat(stock(seed.variantId())).isEqualTo(3);   // intacto.
    }

    @Test
    @DisplayName("fluxo feliz separando → enviado → entregue notifica cada transição (enviado = texto de entrega), sem restock")
    void happyFlow_notifiesEach() {
        SeedResult seed = seedOrder();   // fulfillment = entrega.
        service.updateStatus(COMPANY, seed.order().id(), "separando", null);       // 1 envio (aceite).
        service.updateStatus(COMPANY, seed.order().id(), "enviado", null);         // 2.
        ModaInfantilOrder delivered = service.updateStatus(COMPANY, seed.order().id(), "entregue", null);   // 3.

        assertThat(delivered.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(3);
        assertThat(fakeEvolution.sent().get(1).text()).contains("enviado");   // entrega.
        assertThat(fakeEvolution.sent().get(2).text()).contains("entregue");
        // nenhum desses status devolve estoque.
        assertThat(stock(seed.variantId())).isEqualTo(3);
        assertThat(delivered.stockReturned()).isFalse();
    }

    @Test
    @DisplayName("enviado em RETIRADA → texto 'pronto para retirada'")
    void enviado_retirada_text() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Pijama", null, "pijamas", 9000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "6a", "Azul", null, null, 3);
        ModaInfantilOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(v.id(), 1)), null, null);

        service.updateStatus(COMPANY, order.id(), "separando", null);   // 1 (aceite).
        service.updateStatus(COMPANY, order.id(), "enviado", null);     // 2.
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(1).text()).contains("retirada");
    }

    // -------------------------------------------------------------------------
    // Onda 1 do backlog (cupom + avise-me quando voltar)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("cupom válido aplica desconto clampado; inválido NÃO aborta (sai sem desconto)")
    void couponAppliesAndInvalidIsIgnored() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Kit", null, "menina", 10000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "2a", "Rosa", null, null, 10);
        jdbcTemplate.update(
            "insert into moda_infantil_coupons (company_id, code, kind, value, active) values (?, 'DEZ10', 'percent', 10, true)",
            COMPANY);

        ModaInfantilOrder comCupom = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(v.id(), 2)), "DEZ10", null);
        assertThat(comCupom.discountCents()).isEqualTo(2000);
        assertThat(comCupom.totalCents()).isEqualTo(18000);
        assertThat(comCupom.couponCode()).isEqualTo("DEZ10");

        ModaInfantilOrder semCupom = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(v.id(), 1)), "NAOEXISTE", null);
        assertThat(semCupom.discountCents()).isZero();
        assertThat(semCupom.couponCode()).isNull();
    }

    @Test
    @DisplayName("avise-me: registra 1x por contato+variante e a reposição 0→N notifica e marca")
    void stockAlertRegisterAndNotify() {
        ModaInfantilProduct p = productService.create(COMPANY, USER, "Body", null, "menina", 4000);
        ModaInfantilVariant v = productService.addVariant(COMPANY, USER, p.id(), "2a", "Rosa", null, null, 0);

        assertThat(alertService.register(COMPANY, contactId, v.id())).isTrue();
        assertThat(alertService.register(COMPANY, contactId, v.id())).isFalse();   // duplicata é no-op.

        fakeEvolution.reset();
        productService.updateVariant(COMPANY, USER, p.id(), v.id(), null, null, null, null, 8, null, false);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("VOLTOU");

        // fila esvaziada: repor de novo não re-notifica.
        fakeEvolution.reset();
        productService.updateVariant(COMPANY, USER, p.id(), v.id(), null, null, null, null, 0, null, false);
        productService.updateVariant(COMPANY, USER, p.id(), v.id(), null, null, null, null, 5, null, false);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-moda-infantil";
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
