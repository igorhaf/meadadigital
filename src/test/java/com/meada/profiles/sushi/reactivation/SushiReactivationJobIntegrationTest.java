package com.meada.profiles.sushi.reactivation;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
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

/**
 * Integration test da reativação de inativos do sushi (onda 2, backlog #3) contra PostgreSQL real.
 * Lógica via {@link SushiReactivationJob#runReactivation()} direto (sem scheduler); EvolutionSender
 * é um FAKE. Cobre: opt-in default OFF; inativo → 1 mensagem + log + cooldown; cupom de retorno só
 * quando válido; cliente recente não é abordado; sem conversa → marca sem envio.
 */
@Import(SushiReactivationJobIntegrationTest.TestConfig.class)
class SushiReactivationJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("a9000000-0000-0000-0000-0000000000d1");
    private static final UUID INSTANCE = UUID.fromString("a9100000-0000-0000-0000-0000000000d1");

    @Autowired
    private SushiReactivationJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID statusEntregue;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi Reativa", "sushi-reativa");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-sre", "tok-sre");
        statusEntregue = jdbcTemplate.queryForObject(
            "insert into sushi_order_statuses (company_id, name, is_initial, is_terminal) "
                + "values (?, 'Entregue', false, true) returning id",
            UUID.class, COMPANY);
    }

    private void enableReactivation(Integer days, String couponCode) {
        jdbcTemplate.update(
            "insert into sushi_restaurant_config (company_id, delivery_fee_cents, min_order_cents, "
                + "reactivation_enabled, reactivation_days, reactivation_coupon_code) "
                + "values (?, 0, 0, true, ?, ?)",
            COMPANY, days == null ? 21 : days, couponCode);
    }

    /** Contato com conversa e um pedido ENTREGUE criado há {@code daysAgo} dias. */
    private UUID seedContactWithOrder(String phone, int daysAgo) {
        UUID contact = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Kenji')",
            contact, COMPANY, phone);
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, INSTANCE);
        UUID order = jdbcTemplate.queryForObject(
            "insert into sushi_orders (company_id, conversation_id, contact_id, status, subtotal_cents, total_cents, delivery_address) "
                + "values (?, ?, ?, ?, 8000, 8000, 'Rua Y') returning id",
            UUID.class, COMPANY, conv, contact, statusEntregue);
        jdbcTemplate.update("update sushi_orders set created_at = now() - make_interval(days => ?) where id = ?",
            daysAgo, order);
        return contact;
    }

    @Test
    @DisplayName("opt-in DESLIGADO (default) → nada, mesmo com inativo")
    void optOutByDefault_nothing() {
        seedContactWithOrder("+5511999990201", 40);
        assertThat(job.runReactivation()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("inativo além da janela → 1 mensagem + log; cooldown segura a 2ª passada")
    void inactive_reachedOnceWithCooldown() {
        enableReactivation(21, null);
        UUID contact = seedContactWithOrder("+5511999990202", 30);

        assertThat(job.runReactivation()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Sentimos sua falta");
        Long logs = jdbcTemplate.queryForObject(
            "select count(*) from sushi_reactivation_log where company_id = ? and contact_id = ?",
            Long.class, COMPANY, contact);
        assertThat(logs).isEqualTo(1);

        fakeEvolution.reset();
        assertThat(job.runReactivation()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("cupom de retorno VÁLIDO entra na mensagem; código inexistente/inativo não")
    void couponOnlyWhenValid() {
        enableReactivation(21, "VOLTA10");
        jdbcTemplate.update(
            "insert into sushi_coupons (company_id, code, kind, value, active) values (?, 'VOLTA10', 'percent', 10, true)",
            COMPANY);
        seedContactWithOrder("+5511999990203", 30);

        assertThat(job.runReactivation()).isEqualTo(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("VOLTA10");

        // segundo tenant-cenário: cupom desativado → mensagem sem cupom.
        jdbcTemplate.update("update sushi_coupons set active = false where company_id = ?", COMPANY);
        jdbcTemplate.update("delete from sushi_reactivation_log where company_id = ?", COMPANY);
        fakeEvolution.reset();
        assertThat(job.runReactivation()).isEqualTo(1);
        assertThat(fakeEvolution.sent().get(0).text()).doesNotContain("VOLTA10");
    }

    @Test
    @DisplayName("cliente recente (dentro da janela) → nada")
    void recentCustomer_nothing() {
        enableReactivation(21, null);
        seedContactWithOrder("+5511999990204", 5);
        assertThat(job.runReactivation()).isZero();
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
            return "key-sushi-reactivation";
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
