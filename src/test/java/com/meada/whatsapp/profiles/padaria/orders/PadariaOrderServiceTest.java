package com.meada.whatsapp.profiles.padaria.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.padaria.PadariaConfigRepository;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuItem;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuOption;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuService;
import com.meada.whatsapp.profiles.padaria.orders.PadariaOrderRepository.AddressRequiredException;
import com.meada.whatsapp.profiles.padaria.orders.PadariaOrderRepository.InvalidOptionException;
import com.meada.whatsapp.profiles.padaria.orders.PadariaOrderRepository.LeadTimeViolationException;
import com.meada.whatsapp.profiles.padaria.orders.PadariaOrderService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Testa o PadariaOrderService (camada 8.8 / perfil padaria): as DUAS escapadas (ESCAPADA 1 — data
 * condicional com lead time; ESCAPADA 2 — personalização/cake_message), o fulfillment (retirada sem
 * taxa/endereço × entrega exige endereço + soma taxa), o recálculo que DESCARTA o total da IA, e a
 * máquina de status / gate de aceite com o funil que DIVERGE no fim. EvolutionSender é um fake que
 * registra os envios. Clone do FloriculturaOrderServiceTest + as escapadas.
 */
@Import(PadariaOrderServiceTest.TestConfig.class)
class PadariaOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PadariaOrderService service;
    @Autowired
    private PadariaMenuService menuService;
    @Autowired
    private PadariaConfigRepository configRepository;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8800000-0000-0000-0000-000000000073");
    private static final UUID USER = UUID.fromString("d8800000-0000-0000-0000-000000000073");
    private UUID conversationId;
    private UUID contactId;

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'padaria')",
            COMPANY, "Padaria O", "padaria-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@padaria-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990073", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        // taxa de entrega 700, lead default 1.
        configRepository.upsert(COMPANY, 700, 0, 1);
    }

    private PadariaMenuItem readyItem() {
        return menuService.create(COMPANY, USER, "Pão Francês", null, 100, "paes", false, null, null);
    }

    private PadariaMenuItem cakeItem(int leadDays) {
        return menuService.create(COMPANY, USER, "Bolo", null, 8000, "bolos_encomenda", true, leadDays, null);
    }

    // ===== ESCAPADA 1 — data condicional + lead time =========================

    @Test
    @DisplayName("pronta-entrega SEM data → OK (data null, retirada sem taxa)")
    void readyOnly_noDate_ok() {
        PadariaMenuItem pao = readyItem();
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(pao.id(), 2, List.of(), null)), null, null, null);
        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(order.pickupOrDeliveryDate()).isNull();
        assertThat(order.subtotalCents()).isEqualTo(200);
        assertThat(order.deliveryFeeCents()).isZero();         // retirada sem taxa.
        assertThat(order.totalCents()).isEqualTo(200);
        assertThat(fakeEvolution.sent()).isEmpty();            // aguardando é silencioso.
    }

    @Test
    @DisplayName("item sob encomenda SEM data → LeadTimeViolationException (422) com a 1ª data possível")
    void madeToOrder_noDate_violates() {
        PadariaMenuItem bolo = cakeItem(2);
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(bolo.id(), 1, List.of(), "Parabéns")), null, null, null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(2));
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("item sob encomenda com data ANTES do lead → LeadTimeViolationException (422) com earliest")
    void madeToOrder_dateTooSoon_violates() {
        PadariaMenuItem bolo = cakeItem(3);
        LocalDate tooSoon = today().plusDays(1);   // lead é 3.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(bolo.id(), 1, List.of(), null)), tooSoon, "manha", null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(3));
    }

    @Test
    @DisplayName("item sob encomenda com data válida (>= today+lead) → OK")
    void madeToOrder_validDate_ok() {
        PadariaMenuItem bolo = cakeItem(2);
        LocalDate ok = today().plusDays(2);
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bolo.id(), 1, List.of(), "Feliz Aniversário")), ok, "tarde", null);
        assertThat(order.pickupOrDeliveryDate()).isEqualTo(ok);
        assertThat(order.deliveryPeriod()).isEqualTo("tarde");
        assertThat(order.items().get(0).madeToOrder()).isTrue();
        assertThat(order.items().get(0).cakeMessage()).isEqualTo("Feliz Aniversário");
    }

    @Test
    @DisplayName("data exigida = MAX dos leads quando há múltiplos itens sob encomenda")
    void madeToOrder_maxOfLeads() {
        PadariaMenuItem bolo2 = cakeItem(2);
        PadariaMenuItem bolo5 = menuService.create(COMPANY, USER, "Bolo 5 andares", null, 30000,
            "bolos_encomenda", true, 5, null);
        // data = today+4 é OK pro bolo de lead 2, mas NÃO pro de lead 5 → viola, earliest = today+5.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(bolo2.id(), 1, List.of(), null),
                        new OrderLineInput(bolo5.id(), 1, List.of(), null)),
                today().plusDays(4), "manha", null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(5));
    }

    @Test
    @DisplayName("item sob encomenda usa lead_time_days_default da config quando o item não tem lead próprio")
    void madeToOrder_usesConfigDefault() {
        configRepository.upsert(COMPANY, 700, 0, 4);   // lead default 4.
        PadariaMenuItem bolo = menuService.create(COMPANY, USER, "Bolo padrão", null, 6000,
            "bolos_encomenda", true, null, null);       // sem lead próprio → usa o default.
        LeadTimeViolationException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "retirada", null,
                List.of(new OrderLineInput(bolo.id(), 1, List.of(), null)), today().plusDays(2), "manha", null),
            LeadTimeViolationException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.earliestDate()).isEqualTo(today().plusDays(4));
    }

    // ===== ESCAPADA 2 — personalização + recálculo ===========================

    @Test
    @DisplayName("personalização: unit_price = base + Σ deltas; cake_message snapshot; total da IA descartado")
    void personalization_recalc() {
        PadariaMenuItem bolo = cakeItem(1);
        PadariaMenuOption recheio = menuService.addOption(COMPANY, USER, bolo.id(), "Recheio", "Brigadeiro", 500, 0);
        PadariaMenuOption tamanho = menuService.addOption(COMPANY, USER, bolo.id(), "Tamanho", "1kg", 1500, 1);

        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bolo.id(), 2, List.of(recheio.id(), tamanho.id()), "Bom dia!")),
            today().plusDays(1), "manha", null);

        // unit_price = 8000 + 500 + 1500 = 10000; subtotal = 10000 * 2 = 20000; retirada sem taxa.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(10000);
        assertThat(order.items().get(0).options()).hasSize(2);
        assertThat(order.items().get(0).cakeMessage()).isEqualTo("Bom dia!");
        assertThat(order.subtotalCents()).isEqualTo(20000);
        assertThat(order.totalCents()).isEqualTo(20000);
    }

    @Test
    @DisplayName("option_id de outro item → InvalidOptionException, pedido NÃO criado")
    void invalidOption_aborts() {
        PadariaMenuItem bolo = cakeItem(1);
        PadariaMenuItem outro = menuService.create(COMPANY, USER, "Torta", null, 4000, "tortas", true, 1, null);
        PadariaMenuOption optDeOutro = menuService.addOption(COMPANY, USER, outro.id(), "Sabor", "Limão", 300, 0);

        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(bolo.id(), 1, List.of(optDeOutro.id()), null)),
            today().plusDays(1), "manha", null))
            .isInstanceOf(InvalidOptionException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    // ===== fulfillment retirada × entrega ====================================

    @Test
    @DisplayName("entrega SEM endereço → AddressRequiredException (422), pedido NÃO criado")
    void delivery_noAddress_violates() {
        PadariaMenuItem pao = readyItem();
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "entrega", null,
            List.of(new OrderLineInput(pao.id(), 1, List.of(), null)), null, null, null))
            .isInstanceOf(AddressRequiredException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from padaria_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("entrega COM endereço → soma a delivery_fee da config")
    void delivery_withAddress_addsFee() {
        PadariaMenuItem pao = readyItem();
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "entrega", "Rua X 1",
            List.of(new OrderLineInput(pao.id(), 3, List.of(), null)), null, null, null);
        assertThat(order.fulfillment()).isEqualTo("entrega");
        assertThat(order.deliveryAddress()).isEqualTo("Rua X 1");
        assertThat(order.subtotalCents()).isEqualTo(300);
        assertThat(order.deliveryFeeCents()).isEqualTo(700);    // taxa somada só na entrega.
        assertThat(order.totalCents()).isEqualTo(1000);
    }

    @Test
    @DisplayName("retirada ignora endereço/taxa (mesmo se endereço vier no payload, não persiste taxa)")
    void pickup_noFee() {
        PadariaMenuItem pao = readyItem();
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", "Rua Y 9",
            List.of(new OrderLineInput(pao.id(), 1, List.of(), null)), null, null, null);
        assertThat(order.deliveryFeeCents()).isZero();
        assertThat(order.deliveryAddress()).isNull();           // retirada não persiste endereço.
        assertThat(order.totalCents()).isEqualTo(100);
    }

    // ===== Máquina de status / gate de aceite ================================

    @Test
    @DisplayName("pedido nasce 'aguardando' e NÃO dispara notificação na criação")
    void create_isAguardando_andSilent() {
        service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("retirada: aguardando → em_preparo (aceite) → pronto → retirado, com notificações de aceite/pronto")
    void retiradaFlow() {
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);

        service.updateStatus(COMPANY, order.id(), "em_preparo", null);   // 1 envio (aceito).
        service.updateStatus(COMPANY, order.id(), "pronto", null);       // 2 (pronto).
        PadariaOrder retirado = service.updateStatus(COMPANY, order.id(), "retirado", null);  // retirado é silencioso.

        assertThat(retirado.status()).isEqualTo("retirado");
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(0).text()).contains("em preparo");
        assertThat(fakeEvolution.sent().get(1).text()).contains("pronto");
    }

    @Test
    @DisplayName("entrega: pronto → saiu_entrega → entregue, notifica cada uma")
    void entregaFlow() {
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "entrega", "Rua X 1",
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);
        service.updateStatus(COMPANY, order.id(), "pronto", null);
        fakeEvolution.reset();
        service.updateStatus(COMPANY, order.id(), "saiu_entrega", null);
        PadariaOrder entregue = service.updateStatus(COMPANY, order.id(), "entregue", null);

        assertThat(entregue.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(0).text()).contains("saiu pra entrega");
        assertThat(fakeEvolution.sent().get(1).text()).contains("entregue");
    }

    @Test
    @DisplayName("recusa (aguardando → recusado) com motivo → terminal + notificação contém o motivo defensivo")
    void reject_withReason() {
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);

        PadariaOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "sem fermento hoje");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("sem fermento hoje");
        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("não conseguimos atender seu pedido");
        assertThat(text).contains("sem fermento hoje");
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        PadariaOrder order = service.create(COMPANY, conversationId, contactId, "retirada", null,
            List.of(new OrderLineInput(readyItem().id(), 1, List.of(), null)), null, null, null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
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
            return "key-padaria";
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
