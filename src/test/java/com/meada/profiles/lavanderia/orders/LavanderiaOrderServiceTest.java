package com.meada.profiles.lavanderia.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.lavanderia.config.LavanderiaConfigService;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderService.AddressRequiredException;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderService.BelowMinimumOrderException;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderService.CollectDateInPastException;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderService.InvalidStatusTransitionException;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderService.TurnaroundViolationOrderException;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderRepository.InvalidOptionException;
import com.meada.profiles.lavanderia.services.LavanderiaService;
import com.meada.profiles.lavanderia.services.LavanderiaServiceCatalogService;
import com.meada.profiles.lavanderia.services.LavanderiaServiceOption;
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
 * Testa o LavanderiaOrderService (camada 8.10) — a ESCAPADA das DUAS DATAS (collect + delivery
 * materializada por MAX(turnaround)) e o gate de aceite + a máquina de status. EvolutionSender é um
 * fake que registra os envios. Clone do FloriculturaOrderServiceTest + a escapada.
 */
@Import(LavanderiaOrderServiceTest.TestConfig.class)
class LavanderiaOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LavanderiaOrderService service;
    @Autowired
    private LavanderiaServiceCatalogService catalogService;
    @Autowired
    private LavanderiaConfigService configService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("1a000000-0000-0000-0000-000000000073");
    private static final UUID USER = UUID.fromString("1b000000-0000-0000-0000-000000000073");
    private UUID conversationId;
    private UUID contactId;

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'lavanderia')",
            COMPANY, "Lavanderia O", "lavanderia-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@lavanderia-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990173", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private LavanderiaService svc(String name, int price, int turnaround) {
        return catalogService.create(COMPANY, USER, name, null, price, "lavar", turnaround, null);
    }

    // ---- ESCAPADA: materialização da delivery_date --------------------------

    @Test
    @DisplayName("pedido simples: delivery_date materializada = collect + turnaround do único serviço")
    void simpleOrder_materializesDelivery() {
        LavanderiaService s = svc("Lavar camisa", 800, 2);
        LocalDate collect = today().plusDays(1);
        LavanderiaOrder order = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(s.id(), 3, List.of())), null, collect, null, "manha", null, false);

        assertThat(order.status()).isEqualTo("aguardando");
        assertThat(order.collectDate()).isEqualTo(collect);
        assertThat(order.deliveryDate()).isEqualTo(collect.plusDays(2));
        assertThat(order.subtotalCents()).isEqualTo(2400);   // 800 * 3
        assertThat(order.totalCents()).isEqualTo(2400);      // sem taxa
        assertThat(order.items().get(0).turnaroundSnapshot()).isEqualTo(2);
        assertThat(fakeEvolution.sent()).isEmpty();          // aguardando é silencioso
    }

    @Test
    @DisplayName("ESCAPADA — delivery_date = collect + MAX(turnaround), NÃO soma: serviços com prazo 1 e 3 → +3")
    void maxTurnaround_notSum() {
        LavanderiaService rapido = svc("Passar (1 dia)", 500, 1);
        LavanderiaService lento = svc("Lavagem a seco (3 dias)", 3000, 3);
        LocalDate collect = today().plusDays(2);

        LavanderiaOrder order = service.create(COMPANY, conversationId, contactId, "Rua Y",
            List.of(new OrderLineInput(rapido.id(), 1, List.of()),
                    new OrderLineInput(lento.id(), 1, List.of())),
            null, collect, null, "tarde", null, false);

        // MAX(1, 3) = 3 — NÃO 4 (soma), NÃO 1.
        assertThat(order.deliveryDate()).isEqualTo(collect.plusDays(3));
        assertThat(order.deliveryDate()).isNotEqualTo(collect.plusDays(4));
        assertThat(order.deliveryDate()).isNotEqualTo(collect.plusDays(1));
    }

    @Test
    @DisplayName("delivery_date pedida ANTES de collect+MAX(turnaround) → 422 turnaround_violation (devolve a 1ª possível)")
    void deliveryBeforeMin_turnaroundViolation() {
        LavanderiaService s = svc("Lavar e passar", 1200, 3);
        LocalDate collect = today().plusDays(1);
        LocalDate tooSoon = collect.plusDays(1);   // antes de collect + 3.

        TurnaroundViolationOrderException ex = catchThrowableOfType(
            () -> service.create(COMPANY, conversationId, contactId, "Rua Z",
                List.of(new OrderLineInput(s.id(), 1, List.of())), null, collect, tooSoon, "manha", null, false),
            TurnaroundViolationOrderException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.firstPossibleDeliveryDate()).isEqualTo(collect.plusDays(3));
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("delivery_date pedida >= collect+MAX → aceita a data pedida (não materializa por cima)")
    void deliveryRequested_valid_keptAsIs() {
        LavanderiaService s = svc("Lavar", 800, 2);
        LocalDate collect = today().plusDays(1);
        LocalDate requested = collect.plusDays(5);   // depois da 1ª possível (collect+2).

        LavanderiaOrder order = service.create(COMPANY, conversationId, contactId, "Rua W",
            List.of(new OrderLineInput(s.id(), 1, List.of())), null, collect, requested, "manha", null, false);
        assertThat(order.deliveryDate()).isEqualTo(requested);
    }

    @Test
    @DisplayName("collect_date no PASSADO → 422 CollectDateInPast, 0 pedidos")
    void collectDateInPast() {
        LavanderiaService s = svc("Lavar", 800, 1);
        LocalDate ontem = today().minusDays(1);
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(s.id(), 1, List.of())), null, ontem, null, "manha", null, false))
            .isInstanceOf(CollectDateInPastException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("endereço ausente → 422 AddressRequired")
    void addressMissing() {
        LavanderiaService s = svc("Lavar", 800, 1);
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "  ",
            List.of(new OrderLineInput(s.id(), 1, List.of())), null, today().plusDays(1), null, "manha", null, false))
            .isInstanceOf(AddressRequiredException.class);
    }

    @Test
    @DisplayName("opção delta soma ao unit_price; total recalculado (descarta o da IA)")
    void optionDeltas_total() {
        LavanderiaService s = svc("Lavar e passar", 1200, 2);
        LavanderiaServiceOption engomar = catalogService.addOption(COMPANY, USER, s.id(), "Acabamento", "Engomar", 300, 0);
        configService.update(COMPANY, USER, 700, 0, 1, true, 50, 1, true, true, 2, false, 30, null);   // taxa 700.

        LavanderiaOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(s.id(), 2, List.of(engomar.id()))), null,
            today().plusDays(1), null, "manha", null, false);

        // unit_price = 1200 + 300 = 1500; subtotal = 1500*2 = 3000; total = 3000 + 700 = 3700.
        assertThat(order.items().get(0).unitPriceCents()).isEqualTo(1500);
        assertThat(order.items().get(0).options()).hasSize(1);
        assertThat(order.subtotalCents()).isEqualTo(3000);
        assertThat(order.totalCents()).isEqualTo(3700);
    }

    @Test
    @DisplayName("subtotal abaixo do mínimo → 422 BelowMinimum")
    void belowMinimum() {
        LavanderiaService s = svc("Lavar", 800, 1);
        configService.update(COMPANY, USER, 0, 5000, 1, true, 50, 1, true, true, 2, false, 30, null);   // mínimo 5000.
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(s.id(), 1, List.of())), null, today().plusDays(1), null, "manha", null, false))
            .isInstanceOf(BelowMinimumOrderException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("opção fantasma (de outro serviço) → InvalidOption, 0 pedidos")
    void invalidOption_aborts() {
        LavanderiaService s = svc("Lavar", 800, 1);
        LavanderiaService outro = svc("Passar", 500, 1);
        LavanderiaServiceOption optDeOutro = catalogService.addOption(COMPANY, USER, outro.id(), "Acabamento", "Vapor", 100, 0);
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(s.id(), 1, List.of(optDeOutro.id()))), null,
            today().plusDays(1), null, "manha", null, false))
            .isInstanceOf(InvalidOptionException.class);
        assertThat(jdbcTemplate.queryForObject("select count(*) from lavanderia_orders", Long.class)).isZero();
    }

    @Test
    @DisplayName("snapshots de nome+turnaround preservados mesmo após alterar o serviço")
    void snapshotsPreserved() {
        LavanderiaService s = svc("Lavar camisa", 800, 2);
        LavanderiaOrder order = service.create(COMPANY, conversationId, contactId, "Rua X",
            List.of(new OrderLineInput(s.id(), 1, List.of())), null, today().plusDays(1), null, "manha", null, false);
        // muda o serviço depois.
        catalogService.update(COMPANY, USER, s.id(), "Outro nome", null, 9999, null, 9, null, null);

        LavanderiaOrder reloaded = service.get(COMPANY, order.id()).orElseThrow();
        assertThat(reloaded.items().get(0).serviceName()).isEqualTo("Lavar camisa");
        assertThat(reloaded.items().get(0).turnaroundSnapshot()).isEqualTo(2);
        assertThat(reloaded.items().get(0).unitPriceCents()).isEqualTo(800);
    }

    // ---- Gate de aceite + máquina de status ---------------------------------

    private LavanderiaOrder seedOrder() {
        LavanderiaService s = svc("Lavar camisa", 800, 1);
        return service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(s.id(), 2, List.of())), null, today().plusDays(1), null, "manha", null, false);
    }

    @Test
    @DisplayName("aceite (aguardando → coletado) → notificação 'recebemos suas peças'")
    void accept_notifies() {
        LavanderiaOrder order = seedOrder();
        LavanderiaOrder updated = service.updateStatus(COMPANY, order.id(), "coletado", null);
        assertThat(updated.status()).isEqualTo("coletado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Recebemos suas peças");
    }

    @Test
    @DisplayName("recusa (aguardando → recusado) com motivo → terminal + notificação defensiva com o motivo")
    void reject_withReason() {
        LavanderiaOrder order = seedOrder();
        LavanderiaOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "fora da área de coleta");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("fora da área de coleta");
        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("não conseguimos atender seu pedido");
        assertThat(text).contains("fora da área de coleta");
    }

    @Test
    @DisplayName("fluxo feliz coletado → em_processo → pronto → saiu_entrega → entregue (em_processo silencioso)")
    void happyFlow() {
        LavanderiaOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "coletado", null);     // notifica (1)
        service.updateStatus(COMPANY, order.id(), "em_processo", null);  // silencioso
        service.updateStatus(COMPANY, order.id(), "pronto", null);       // notifica (2)
        service.updateStatus(COMPANY, order.id(), "saiu_entrega", null); // notifica (3)
        LavanderiaOrder delivered = service.updateStatus(COMPANY, order.id(), "entregue", null); // notifica (4)

        assertThat(delivered.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(4);
        assertThat(fakeEvolution.sent().get(1).text()).contains("prontas");
        assertThat(fakeEvolution.sent().get(2).text()).contains("saíram para entrega");
        assertThat(fakeEvolution.sent().get(3).text()).contains("entregue");
    }

    @Test
    @DisplayName("transição inválida (aguardando → pronto) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        LavanderiaOrder order = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "pronto", null))
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
            return "key-lavanderia";
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
