package com.meada.profiles.comida.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.comida.orders.ComidaOrder;
import com.meada.profiles.comida.orders.ComidaOrderService;
import com.meada.profiles.comida.orders.OrderLineInput;
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
 * Integration test da onda Comida 2 (backlog #3/#5/#9/#12): retirada no balcão (sem taxa/endereço),
 * janela do delivery (422 defensivo), auto-entrega opt-in e reativação de inativo (opt-in OFF).
 * EvolutionSender é um FAKE.
 */
@Import(ComidaOnda2IntegrationTest.TestConfig.class)
class ComidaOnda2IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("c9000000-0000-0000-0000-000000000114");
    private static final UUID INSTANCE = UUID.fromString("c9100000-0000-0000-0000-000000000114");

    @Autowired
    private ComidaOrderService orderService;
    @Autowired
    private ComidaReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID itemId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'comida')",
            COMPANY, "Comida Onda2", "comida-onda2");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cm2", "tok-cm2");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Leo')",
            contactId, COMPANY, "+5511999990401");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        jdbcTemplate.update("insert into comida_config (company_id, delivery_fee_cents) values (?, 800)", COMPANY);
        itemId = jdbcTemplate.queryForObject(
            "insert into comida_menu_items (company_id, name, price_cents, category) "
                + "values (?, 'X-Bacon', 3000, 'lanches') returning id",
            UUID.class, COMPANY);
    }

    private ComidaOrder create(String fulfillment, String address) {
        return orderService.create(COMPANY, conversationId, contactId, address,
            List.of(new OrderLineInput(itemId, 1, List.of())), null, null, fulfillment, null);
    }

    @Test
    @DisplayName("retirada: sem taxa e sem endereço; entrega soma a taxa")
    void fulfillment() {
        ComidaOrder entrega = create("entrega", "Rua B, 22");
        assertThat(entrega.fulfillment()).isEqualTo("entrega");
        assertThat(entrega.totalCents()).isEqualTo(3800);

        ComidaOrder retirada = create("retirada", null);
        assertThat(retirada.fulfillment()).isEqualTo("retirada");
        assertThat(retirada.deliveryFeeCents()).isZero();
        assertThat(retirada.totalCents()).isEqualTo(3000);
        assertThat(retirada.deliveryAddress()).isNull();
    }

    @Test
    @DisplayName("janela do delivery: fora do horário → 422 outside_hours (defensivo)")
    void outsideHours() {
        // janela impossível (abre e fecha à meia-noite e 1 → agora está fora, exceto exatamente 00:00).
        jdbcTemplate.update(
            "update comida_config set opens_at = '00:00', closes_at = '00:01' where company_id = ?",
            COMPANY);
        java.time.LocalTime now = java.time.LocalTime.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (now.isBefore(java.time.LocalTime.of(0, 1))) {
            return;   // janela de 1 minuto após a meia-noite: pula o teste (flake impossível de rodar).
        }
        assertThatThrownBy(() -> create("entrega", "Rua B, 22"))
            .isInstanceOf(ComidaOrderService.OutsideHoursException.class);
    }

    @Test
    @DisplayName("auto-entrega opt-in: saiu_entrega além de N horas → entregue; reativação OFF/ON")
    void autoDeliverAndReactivation() {
        UUID orderId = create("entrega", "Rua B, 22").id();
        jdbcTemplate.update(
            "update comida_orders set status = 'saiu_entrega', status_updated_at = now() - interval '5 hours' "
                + "where id = ?", orderId);

        assertThat(job.runAutoDeliver()).isZero();   // NULL = desligado.
        jdbcTemplate.update("update comida_config set auto_deliver_hours = 3 where company_id = ?", COMPANY);
        assertThat(job.runAutoDeliver()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select status from comida_orders where id = ?", String.class, orderId)).isEqualTo("entregue");

        // reativação: OFF default; ON → inativo recebe 1 toque.
        jdbcTemplate.update(
            "update comida_orders set created_at = now() - interval '60 days' where id = ?", orderId);
        fakeEvolution.reset();
        assertThat(job.runReactivations()).isZero();
        jdbcTemplate.update(
            "update comida_config set reactivation_enabled = true, reactivation_days = 30 where company_id = ?",
            COMPANY);
        assertThat(job.runReactivations()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Leo");
        fakeEvolution.reset();
        assertThat(job.runReactivations()).isZero();   // cooldown.
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-comida-onda2";
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
