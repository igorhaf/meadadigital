package com.meada.profiles.las.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.las.catalog.LasProduct;
import com.meada.profiles.las.catalog.LasProductService;
import com.meada.profiles.las.catalog.LasVariant;
import com.meada.profiles.las.orders.LasOrderRepository.OutOfStockException;
import com.meada.profiles.las.orders.LasOrderService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o LasOrderService (camada 8.23): o recálculo de preço (variante/base × qtd, total da IA
 * descartado), o snapshot, a ESCAPADA de estoque (decremento + OutOfStockException aborta), e o gate
 * de aceite (aguardando → separando ACEITE notifica / aguardando → recusado RECUSA notifica
 * defensivamente com motivo), transição inválida 409, fluxo feliz separando→enviado→entregue
 * notificando cada, cancelamento, e que {@code aguardando} NÃO notifica na criação. Clone do
 * LingerieOrderServiceTest, eixo size→dye_lot.
 */
@Import(LasOrderServiceTest.TestConfig.class)
class LasOrderServiceTest extends AbstractIntegrationTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private LasOrderService service;
    @Autowired
    private LasProductService productService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8230000-0000-0000-0000-000000000093");
    private static final UUID USER = UUID.fromString("d8230000-0000-0000-0000-000000000093");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'las')",
            COMPANY, "Las O", "las-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@las-o.dev', 'admin')",
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
        jdbcTemplate.update("insert into las_config (company_id, delivery_fee_cents) values (?, 700)", COMPANY);
    }

    /** Cria um pedido de ENTREGA com uma variante de estoque 5 (helper p/ os testes de gate). */
    private LasOrder seedOrder() {
        LasProduct p = productService.create(COMPANY, USER, "Lã", null, "las", 5000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, null, 5);
        return service.create(COMPANY, conversationId, contactId, "entrega", false, "Rua X 1",
            List.of(new OrderLineInput(v.id(), 2)), null, null);
    }

    // ---- Preço + estoque -----------------------------------------------------

    @Test
    @DisplayName("preço = variante (ou base) × qtd; total com fee em entrega; snapshot preservado após editar o produto")
    void price_andSnapshot() {
        LasProduct p = productService.create(COMPANY, USER, "Lã", null, "las", 5000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, 8990, 5);

        LasOrder order = service.create(COMPANY, conversationId, contactId, "entrega", false, "Rua Y 2",
            List.of(new OrderLineInput(v.id(), 2)), null, null);

        // unit_price = 8990 (variante); subtotal = 8990*2 = 17980; total = 17980 + 700 = 18680.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(8990);
        assertThat(order.subtotalCents()).isEqualTo(17980);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);
        assertThat(order.totalCents()).isEqualTo(18680);

        // editar o preço da variante DEPOIS não altera o snapshot do pedido.
        productService.updateVariant(COMPANY, USER, p.id(), v.id(), null, null, null, 99999, null, null, false);
        LasOrder refetched = service.get(COMPANY, order.id()).orElseThrow();
        assertThat(refetched.items().get(0).unitPriceCents()).isEqualTo(8990);   // snapshot preservado.
    }

    @Test
    @DisplayName("create com qtd > estoque → OutOfStockException + ZERO pedidos (estoque intacto)")
    void create_outOfStock_throwsAndNoOrder() {
        LasProduct p = productService.create(COMPANY, USER, "Lã", null, "las", 5000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Nude", "L2024-A", null, null, 1);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "retirada", false, null,
                List.of(new OrderLineInput(v.id(), 2)), null, null))
            .isInstanceOf(OutOfStockException.class);

        Long count = jdbcTemplate.queryForObject("select count(*) from las_orders", Long.class);
        assertThat(count).isZero();
        Integer stock = jdbcTemplate.queryForObject(
            "select stock_qty from las_variants where id = ?", Integer.class, v.id());
        assertThat(stock).isEqualTo(1);   // intacto (rollback).
    }

    // ---- Gate de aceite ------------------------------------------------------

    @Test
    @DisplayName("pedido nasce 'aguardando' e NÃO dispara notificação na criação")
    void create_isAguardando_andSilent() {
        LasOrder order = seedOrder();
        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("aceite (aguardando → separando) → status atualiza + notificação 'aceito/separando'")
    void accept_notifies() {
        LasOrder order = seedOrder();

        LasOrder updated = service.updateStatus(COMPANY, order.id(), "separando", null);
        assertThat(updated.status()).isEqualTo("separando");

        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("separando");
    }

    @Test
    @DisplayName("recusa (aguardando → recusado) com motivo → terminal + notificação defensiva contém o motivo")
    void reject_withReason_notifiesDefensively() {
        LasOrder order = seedOrder();

        LasOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "produto sem estoque na cor");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("produto sem estoque na cor");

        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("Infelizmente não conseguimos aceitar");   // texto fixo defensivo.
        assertThat(text).contains("produto sem estoque na cor");             // motivo concatenado.
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        LasOrder order = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("fluxo feliz separando → enviado → entregue notifica cada transição (enviado = texto de entrega)")
    void happyFlow_notifiesEach() {
        LasOrder order = seedOrder();   // fulfillment = entrega.
        service.updateStatus(COMPANY, order.id(), "separando", null);       // 1 envio (aceite).
        service.updateStatus(COMPANY, order.id(), "enviado", null);         // 2.
        LasOrder delivered = service.updateStatus(COMPANY, order.id(), "entregue", null);   // 3.

        assertThat(delivered.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(3);
        assertThat(fakeEvolution.sent().get(1).text()).contains("enviado");   // entrega.
        assertThat(fakeEvolution.sent().get(2).text()).contains("entregue");
    }

    @Test
    @DisplayName("enviado em RETIRADA → texto 'pronto para retirada'")
    void enviado_retirada_text() {
        LasProduct p = productService.create(COMPANY, USER, "Kit", null, "kits", 9000);
        LasVariant v = productService.addVariant(COMPANY, USER, p.id(), "Azul", "L2024-A", null, null, 3);
        LasOrder order = service.create(COMPANY, conversationId, contactId, "retirada", false, null,
            List.of(new OrderLineInput(v.id(), 1)), null, null);

        service.updateStatus(COMPANY, order.id(), "separando", null);   // 1 (aceite).
        service.updateStatus(COMPANY, order.id(), "enviado", null);     // 2.
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(1).text()).contains("retirada");
    }

    @Test
    @DisplayName("cancelar (separando → cancelado) → terminal + notificação de cancelamento")
    void cancel_notifies() {
        LasOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "separando", null);   // aceite primeiro (1 envio).
        fakeEvolution.reset();
        LasOrder cancelled = service.updateStatus(COMPANY, order.id(), "cancelado", null);
        assertThat(cancelled.status()).isEqualTo("cancelado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelado");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-las";
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
