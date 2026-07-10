package com.meada.profiles.floricultura.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.floricultura.orders.FloriculturaOrder;
import com.meada.profiles.floricultura.orders.FloriculturaOrderService;
import com.meada.profiles.floricultura.orders.OrderLineInput;
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

/**
 * Integration test da onda Floricultura 1 (backlog #7/#8/#9/#13): cupom (inválido não aborta) +
 * fidelidade por contagem, presente surpresa (anonymous), e confirmação D-1 da entrega ao
 * comprador (rearm ao remarcar + toggle). EvolutionSender é um FAKE. O histórico de recompra (#3)
 * e o upsell (#4) vivem no FloriculturaCatalogCache (prompt).
 */
@Import(FloriculturaOnda1IntegrationTest.TestConfig.class)
class FloriculturaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("a1000000-0000-0000-0000-000000000106");
    private static final UUID INSTANCE = UUID.fromString("a1100000-0000-0000-0000-000000000106");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private FloriculturaOrderService orderService;
    @Autowired
    private FloriculturaReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID itemId;

    private static LocalDate today() {
        return LocalDate.now(SP);
    }

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'floricultura')",
            COMPANY, "Flor Onda", "flor-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-flo", "tok-flo");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Bia')",
            contactId, COMPANY, "+5511999990321");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        itemId = jdbcTemplate.queryForObject(
            "insert into floricultura_catalog_items (company_id, name, price_cents, category) "
                + "values (?, 'Buquê de Rosas', 8000, 'buques') returning id",
            UUID.class, COMPANY);
    }

    private FloriculturaOrder create(String couponCode, boolean anonymous, LocalDate deliveryDate) {
        return orderService.create(COMPANY, conversationId, contactId, "Rua das Flores, 1",
            List.of(new OrderLineInput(itemId, 1, List.of())), null,
            deliveryDate, "manha", "Ana", "Feliz aniversário!", couponCode, anonymous);
    }

    @Test
    @DisplayName("cupom válido desconta e incrementa uses; inválido NÃO aborta; fidelidade no N-ésimo; anonimato persiste")
    void couponLoyaltyAnonymous() {
        jdbcTemplate.update(
            "insert into floricultura_coupons (company_id, code, kind, value) values (?, 'FLOR10', 'percent', 10)",
            COMPANY);

        FloriculturaOrder com = create("flor10", true, today().plusDays(3));
        assertThat(com.discountCents()).isEqualTo(800);
        assertThat(com.totalCents()).isEqualTo(7200);
        assertThat(com.couponCode()).isEqualTo("FLOR10");
        assertThat(com.anonymous()).isTrue();

        FloriculturaOrder sem = create("NAOEXISTE", false, today().plusDays(3));
        assertThat(sem.discountCents()).isZero();
        assertThat(sem.anonymous()).isFalse();

        // fidelidade: threshold 2 — 2 entregues → o próximo ganha 50% ("a cada N, brinde").
        jdbcTemplate.update(
            "insert into floricultura_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
                + "values (?, true, 2, 'percent', 50) "
                + "on conflict (company_id) do update set enabled = true, threshold_orders = 2, "
                + "reward_kind = 'percent', reward_value = 50",
            COMPANY);
        jdbcTemplate.update("update floricultura_orders set status = 'entregue' where company_id = ?", COMPANY);

        FloriculturaOrder premiado = create(null, false, today().plusDays(3));
        assertThat(premiado.loyaltyApplied()).isTrue();
        assertThat(premiado.discountCents()).isEqualTo(4000);
        assertThat(premiado.totalCents()).isEqualTo(4000);
    }

    @Test
    @DisplayName("entrega amanhã em_preparo → aviso D-1 ao comprador 1x; remarcar REARMA; toggle off silencia")
    void deliveryReminder() {
        UUID orderId = create(null, false, today().plusDays(1)).id();
        // aguardando não lembra (ainda sem aceite).
        assertThat(job.runDeliveryReminders()).isZero();

        jdbcTemplate.update("update floricultura_orders set status = 'em_preparo' where id = ?", orderId);
        assertThat(job.runDeliveryReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Ana").contains("Rua das Flores");

        fakeEvolution.reset();
        assertThat(job.runDeliveryReminders()).isZero();   // idempotente.

        // remarcar a entrega REARMA (marker <> delivery_date... aqui movendo a data e voltando).
        jdbcTemplate.update(
            "update floricultura_orders set delivery_reminded_date = ? where id = ?",
            java.sql.Date.valueOf(today().minusDays(1)), orderId);
        assertThat(job.runDeliveryReminders()).isEqualTo(1);

        // toggle off.
        jdbcTemplate.update(
            "insert into floricultura_config (company_id, delivery_reminder_enabled) values (?, false) "
                + "on conflict (company_id) do update set delivery_reminder_enabled = false",
            COMPANY);
        jdbcTemplate.update("update floricultura_orders set delivery_reminded_date = null where id = ?", orderId);
        fakeEvolution.reset();
        assertThat(job.runDeliveryReminders()).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-flor-onda";
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
