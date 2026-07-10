package com.meada.profiles.las.waitlist;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.las.catalog.LasProduct;
import com.meada.profiles.las.catalog.LasProductService;
import com.meada.profiles.las.catalog.LasVariant;
import com.meada.profiles.las.orders.LasOrder;
import com.meada.profiles.las.orders.LasOrderService;
import com.meada.profiles.las.orders.OrderLineInput;
import com.meada.profiles.las.reminders.LasReactivationJob;
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
 * Integration test da onda Lãs 1 (backlog #1/#2/#5/#7): lista de espera de dye lot (lote exato ×
 * qualquer lote da cor, hook 0→N), cupom no pedido (inválido não aborta) e reativação de inativo
 * (opt-in OFF). EvolutionSender é um FAKE. A calculadora (#2) é coberta pelo bloco do LasMenuCache.
 */
@Import(LasOnda1IntegrationTest.TestConfig.class)
class LasOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("e0000000-0000-0000-0000-000000000104");
    private static final UUID INSTANCE = UUID.fromString("e0100000-0000-0000-0000-000000000104");
    private static final UUID USER = UUID.fromString("e0200000-0000-0000-0000-000000000104");

    @Autowired
    private LasProductService productService;
    @Autowired
    private LasOrderService orderService;
    @Autowired
    private LasWaitlistService waitlistService;
    @Autowired
    private LasReactivationJob reactivationJob;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private LasProduct product;
    private LasVariant loteA;
    private LasVariant loteB;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'las')",
            COMPANY, "Las Onda", "las-onda");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@las-onda.dev', 'admin')",
            USER, COMPANY);
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-lso", "tok-lso");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Vera')",
            contactId, COMPANY, "+5511999990301");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);

        product = productService.create(COMPANY, USER, "Lã Merino", null, "las", 2000);
        loteA = productService.addVariant(COMPANY, USER, product.id(), "Azul", "L-A", null, null, 0);
        loteB = productService.addVariant(COMPANY, USER, product.id(), "Azul", "L-B", null, null, 5);
    }

    @Test
    @DisplayName("waitlist: lote exato notifica só na reposição DO lote; any_lot notifica em qualquer lote da cor")
    void waitlistDyeLot() {
        UUID contact2 = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Alba')",
            contact2, COMPANY, "+5511999990302");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", UUID.randomUUID(), COMPANY, contact2, INSTANCE);

        // Vera espera o LOTE EXATO L-A; Alba aceita QUALQUER lote da cor Azul.
        assertThat(waitlistService.register(COMPANY, contactId, loteA.id(), false, 8)).isTrue();
        assertThat(waitlistService.register(COMPANY, contact2, loteA.id(), true, null)).isTrue();
        // duplicata pendente é no-op.
        assertThat(waitlistService.register(COMPANY, contactId, loteA.id(), false, 8)).isFalse();

        // Reposição do lote B (outra variante da MESMA cor): notifica só a fila any_lot (Alba).
        productService.updateVariant(COMPANY, USER, product.id(), loteB.id(), null, null, null, null,
            9, null, false);
        // loteB já tinha estoque 5 — 5→9 NÃO é 0→N, nada dispara.
        assertThat(fakeEvolution.sent()).isEmpty();

        jdbcTemplate.update("update las_variants set stock_qty = 0 where id = ?", loteB.id());
        productService.updateVariant(COMPANY, USER, product.id(), loteB.id(), null, null, null, null,
            9, null, false);
        assertThat(fakeEvolution.sent()).hasSize(1);   // só Alba (any_lot).
        assertThat(fakeEvolution.sent().get(0).text()).contains("Alba").contains("Azul");

        // Reposição do lote A (0→N): notifica Vera (lote exato, com qty desejada).
        fakeEvolution.reset();
        productService.updateVariant(COMPANY, USER, product.id(), loteA.id(), null, null, null, null,
            10, null, false);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Vera").contains("L-A").contains("8 novelos");

        // idempotente: nova reposição 0→N não renotifica ninguém (notified_at marcado).
        fakeEvolution.reset();
        jdbcTemplate.update("update las_variants set stock_qty = 0 where id = ?", loteA.id());
        productService.updateVariant(COMPANY, USER, product.id(), loteA.id(), null, null, null, null,
            4, null, false);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("cupom válido desconta (clamp) e incrementa uses; inválido NÃO aborta")
    void coupon() {
        jdbcTemplate.update(
            "insert into las_coupons (company_id, code, kind, value) values (?, 'LOTE20', 'percent', 20)",
            COMPANY);

        LasOrder com = orderService.create(COMPANY, conversationId, contactId, "retirada", false, null,
            List.of(new OrderLineInput(loteB.id(), 2)), "lote20", null);
        assertThat(com.discountCents()).isEqualTo(800);   // 20% de 4000.
        assertThat(com.totalCents()).isEqualTo(3200);
        assertThat(com.couponCode()).isEqualTo("LOTE20");

        LasOrder sem = orderService.create(COMPANY, conversationId, contactId, "retirada", false, null,
            List.of(new OrderLineInput(loteB.id(), 1)), "NAOEXISTE", null);
        assertThat(sem.discountCents()).isZero();
        assertThat(sem.totalCents()).isEqualTo(2000);
    }

    @Test
    @DisplayName("reativação: OFF por default; ON → inativo recebe 1 toque; cooldown = janela")
    void reactivation() {
        LasOrder order = orderService.create(COMPANY, conversationId, contactId, "retirada", false, null,
            List.of(new OrderLineInput(loteB.id(), 1)), null, null);
        jdbcTemplate.update(
            "update las_orders set status = 'entregue', created_at = now() - interval '90 days' where id = ?",
            order.id());

        fakeEvolution.reset();
        assertThat(reactivationJob.runReactivation()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into las_config (company_id, reactivation_enabled, reactivation_days) "
                + "values (?, true, 45) on conflict (company_id) do update set "
                + "reactivation_enabled = true, reactivation_days = 45",
            COMPANY);
        assertThat(reactivationJob.runReactivation()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Vera").contains("lotes novos");

        fakeEvolution.reset();
        assertThat(reactivationJob.runReactivation()).isZero();   // cooldown.
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-las-onda";
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
