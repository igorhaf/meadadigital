package com.meada.profiles.lavanderia.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.lavanderia.orders.LavanderiaOrder;
import com.meada.profiles.lavanderia.orders.LavanderiaOrderService;
import com.meada.profiles.lavanderia.orders.OrderLineInput;
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
 * Integration test da onda Lavanderia 1 (backlog #2/#3/#5/#6/#7/#14): EXPRESS com sobretaxa +
 * turnaround curto, cupom (motor comum), fidelidade por contagem, lembrete de coleta D-1,
 * lembrete de pronto-parado e reativação de inativo (opt-in OFF). EvolutionSender é um FAKE.
 */
@Import(LavanderiaOnda1IntegrationTest.TestConfig.class)
class LavanderiaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("d0000000-0000-0000-0000-000000000103");
    private static final UUID INSTANCE = UUID.fromString("d0100000-0000-0000-0000-000000000103");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private LavanderiaOrderService orderService;
    @Autowired
    private LavanderiaReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID serviceId;

    private static LocalDate today() {
        return LocalDate.now(SP);
    }

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'lavanderia')",
            COMPANY, "Lavanderia Onda", "lavanderia-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-lvo", "tok-lvo");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Lia')",
            contactId, COMPANY, "+5511999990291");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        serviceId = jdbcTemplate.queryForObject(
            "insert into lavanderia_services (company_id, name, price_cents, category, turnaround_days) "
                + "values (?, 'Lavagem de edredom', 5000, 'lavar', 3) returning id",
            UUID.class, COMPANY);
    }

    private LavanderiaOrder create(String couponCode, boolean express) {
        return orderService.create(COMPANY, conversationId, contactId, "Rua A, 10",
            List.of(new OrderLineInput(serviceId, 2, List.of())), null,
            today(), null, "manha", couponCode, express);
    }

    @Test
    @DisplayName("EXPRESS: turnaround curto da config + sobretaxa % somada; toggle off ignora")
    void express() {
        LavanderiaOrder normal = create(null, false);
        assertThat(normal.deliveryDate()).isEqualTo(today().plusDays(3));
        assertThat(normal.totalCents()).isEqualTo(10000);

        LavanderiaOrder exp = create(null, true);
        assertThat(exp.express()).isTrue();
        assertThat(exp.deliveryDate()).isEqualTo(today().plusDays(1));   // express_turnaround default 1.
        assertThat(exp.expressSurchargeCents()).isEqualTo(5000);        // 50% de 10000.
        assertThat(exp.totalCents()).isEqualTo(15000);

        // toggle off → express da tag é ignorado (pedido normal).
        jdbcTemplate.update("insert into lavanderia_config (company_id, express_enabled) values (?, false)",
            COMPANY);
        LavanderiaOrder ignored = create(null, true);
        assertThat(ignored.express()).isFalse();
        assertThat(ignored.deliveryDate()).isEqualTo(today().plusDays(3));
        assertThat(ignored.totalCents()).isEqualTo(10000);
    }

    @Test
    @DisplayName("cupom válido desconta e incrementa uses; inválido NÃO aborta; fidelidade no N-ésimo")
    void couponAndLoyalty() {
        jdbcTemplate.update(
            "insert into lavanderia_coupons (company_id, code, kind, value) values (?, 'LIMPA10', 'percent', 10)",
            COMPANY);

        LavanderiaOrder comCupom = create("limpa10", false);
        assertThat(comCupom.discountCents()).isEqualTo(1000);
        assertThat(comCupom.totalCents()).isEqualTo(9000);
        assertThat(comCupom.couponCode()).isEqualTo("LIMPA10");
        Integer uses = jdbcTemplate.queryForObject(
            "select uses from lavanderia_coupons where company_id = ?", Integer.class, COMPANY);
        assertThat(uses).isEqualTo(1);

        LavanderiaOrder invalido = create("NAOEXISTE", false);
        assertThat(invalido.discountCents()).isZero();
        assertThat(invalido.totalCents()).isEqualTo(10000);

        // fidelidade: threshold 2 — com 2 pedidos ENTREGUES, o próximo ganha 20%.
        jdbcTemplate.update(
            "insert into lavanderia_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
                + "values (?, true, 2, 'percent', 20) "
                + "on conflict (company_id) do update set enabled = true, threshold_orders = 2, "
                + "reward_kind = 'percent', reward_value = 20",
            COMPANY);
        jdbcTemplate.update("update lavanderia_orders set status = 'entregue' where company_id = ?", COMPANY);

        LavanderiaOrder premiado = create(null, false);    // 2 entregues, 2 % 2 == 0 → reward.
        assertThat(premiado.loyaltyApplied()).isTrue();
        assertThat(premiado.discountCents()).isEqualTo(2000);
        assertThat(premiado.totalCents()).isEqualTo(8000);

        jdbcTemplate.update("update lavanderia_orders set status = 'entregue' where company_id = ? and id = ?",
            COMPANY, premiado.id());
        LavanderiaOrder naoPremiado = create(null, false); // 3 entregues, 3 % 2 != 0.
        assertThat(naoPremiado.loyaltyApplied()).isFalse();
        assertThat(naoPremiado.totalCents()).isEqualTo(10000);
    }

    @Test
    @DisplayName("coleta amanhã → lembrete D-1 1x; remarcar REARMA; toggle off silencia")
    void collectReminder() {
        UUID orderId = orderService.create(COMPANY, conversationId, contactId, "Rua A, 10",
            List.of(new OrderLineInput(serviceId, 1, List.of())), null,
            today().plusDays(1), null, "tarde", null, false).id();

        assertThat(job.runCollectReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("AMANHÃ").contains("tarde");

        fakeEvolution.reset();
        assertThat(job.runCollectReminders()).isZero();   // idempotente.

        // remarcar a coleta REARMA.
        jdbcTemplate.update("update lavanderia_orders set collect_date = ? where id = ?",
            java.sql.Date.valueOf(today().plusDays(1)), orderId);   // mesma data → marker igual, não rearma.
        assertThat(job.runCollectReminders()).isZero();
        jdbcTemplate.update("update lavanderia_orders set collect_date = ?, collect_reminded_date = ? where id = ?",
            java.sql.Date.valueOf(today().plusDays(1)), java.sql.Date.valueOf(today().minusDays(2)), orderId);
        assertThat(job.runCollectReminders()).isEqualTo(1);

        // toggle off.
        jdbcTemplate.update("insert into lavanderia_config (company_id, collect_reminder_enabled) values (?, false)",
            COMPANY);
        jdbcTemplate.update("update lavanderia_orders set collect_reminded_date = null where id = ?", orderId);
        fakeEvolution.reset();
        assertThat(job.runCollectReminders()).isZero();
    }

    @Test
    @DisplayName("pronto parado além da janela → 1 toque por episódio")
    void readyReminder() {
        UUID orderId = create(null, false).id();
        jdbcTemplate.update(
            "update lavanderia_orders set status = 'pronto', status_updated_at = now() - interval '3 days' "
                + "where id = ?", orderId);

        fakeEvolution.reset();
        assertThat(job.runReadyReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("prontinhas");

        // 1 por episódio.
        fakeEvolution.reset();
        assertThat(job.runReadyReminders()).isZero();

        // novo episódio (status_updated_at > marker) rearma.
        jdbcTemplate.update(
            "update lavanderia_orders set status_updated_at = now() - interval '2 days' where id = ?", orderId);
        assertThat(job.runReadyReminders()).isZero();   // marker é de agora (> status_updated_at retroagido).
        jdbcTemplate.update(
            "update lavanderia_orders set ready_reminded_at = now() - interval '5 days' where id = ?", orderId);
        assertThat(job.runReadyReminders()).isEqualTo(1);
    }

    @Test
    @DisplayName("reativação: OFF por default; ON → inativo recebe 1 toque com cupom válido citado")
    void reactivation_optIn() {
        UUID orderId = create(null, false).id();
        jdbcTemplate.update(
            "update lavanderia_orders set status = 'entregue', created_at = now() - interval '60 days' "
                + "where id = ?", orderId);

        fakeEvolution.reset();
        assertThat(job.runReactivations()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into lavanderia_config (company_id, reactivation_enabled, reactivation_days, reactivation_coupon_code) "
                + "values (?, true, 30, 'VOLTA15')", COMPANY);
        jdbcTemplate.update(
            "insert into lavanderia_coupons (company_id, code, kind, value) values (?, 'VOLTA15', 'percent', 15)",
            COMPANY);
        assertThat(job.runReactivations()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Lia").contains("VOLTA15");

        // cooldown = a própria janela.
        fakeEvolution.reset();
        assertThat(job.runReactivations()).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-lavanderia-onda";
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
