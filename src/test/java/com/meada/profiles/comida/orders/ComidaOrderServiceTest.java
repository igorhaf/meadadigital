package com.meada.profiles.comida.orders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.comida.menu.ComidaMenuItem;
import com.meada.profiles.comida.menu.ComidaMenuService;
import com.meada.profiles.comida.orders.ComidaOrderService.InvalidStatusTransitionException;
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
 * Testa o ComidaOrderService (camada 8.4): o gate de aceite (ESCAPADA 1 — aguardando → em_preparo
 * ACEITE / aguardando → recusado RECUSA com motivo defensivo), transição inválida 409, fluxo feliz,
 * cancelamento, e que {@code aguardando} NÃO notifica na criação. EvolutionSender é um fake que
 * registra os envios. Clone do SushiOrderServiceTest + a ESCAPADA 1.
 */
@Import(ComidaOrderServiceTest.TestConfig.class)
class ComidaOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ComidaOrderService service;
    @Autowired
    private ComidaMenuService menuService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000073");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000073");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'comida')",
            COMPANY, "Comida O", "comida-o");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no ComidaMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@comida-o.dev', 'admin')",
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
    }

    private ComidaOrder seedOrder() {
        ComidaMenuItem item = menuService.create(COMPANY, USER, "X-Burger", null, 2500, "lanches");
        return service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item.id(), 2, List.of())), null, null, null);
    }

    @Test
    @DisplayName("pedido nasce 'aguardando' e NÃO dispara notificação na criação (ESCAPADA 1)")
    void create_isAguardando_andSilent() {
        ComidaOrder order = seedOrder();
        assertThat(order.status()).isEqualTo("aguardando");
        // 'aguardando' é silencioso — a IA já confirmou o recebimento na mensagem.
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("aceite (aguardando → em_preparo) → status atualiza + notificação 'aceito/preparo' (ESCAPADA 1)")
    void accept_notifies() {
        ComidaOrder order = seedOrder();

        ComidaOrder updated = service.updateStatus(COMPANY, order.id(), "em_preparo", null);
        assertThat(updated.status()).isEqualTo("em_preparo");

        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("entrou em preparo");
    }

    @Test
    @DisplayName("recusa (aguardando → recusado) com motivo → terminal + notificação contém o motivo defensivo (ESCAPADA 1)")
    void reject_withReason_notifiesDefensively() {
        ComidaOrder order = seedOrder();

        ComidaOrder rejected = service.updateStatus(COMPANY, order.id(), "recusado", "fora da área de entrega");
        assertThat(rejected.status()).isEqualTo("recusado");
        assertThat(rejected.rejectionReason()).isEqualTo("fora da área de entrega");

        assertThat(fakeEvolution.sent()).hasSize(1);
        String text = fakeEvolution.sent().get(0).text();
        assertThat(text).contains("Infelizmente não conseguimos aceitar");   // texto fixo defensivo.
        assertThat(text).contains("fora da área de entrega");                // motivo concatenado.
    }

    @Test
    @DisplayName("transição inválida (aguardando → entregue) → InvalidStatusTransitionException (409), nada enviado")
    void invalidTransition() {
        ComidaOrder order = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue", null))
            .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("fluxo feliz em_preparo → saiu_entrega → entregue notifica cada transição")
    void happyFlow_notifiesEach() {
        ComidaOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);       // 1 envio (aceite).
        service.updateStatus(COMPANY, order.id(), "saiu_entrega", null);     // 2.
        ComidaOrder delivered = service.updateStatus(COMPANY, order.id(), "entregue", null);   // 3.

        assertThat(delivered.status()).isEqualTo("entregue");
        assertThat(fakeEvolution.sent()).hasSize(3);
        assertThat(fakeEvolution.sent().get(1).text()).contains("saiu pra entrega");
        assertThat(fakeEvolution.sent().get(2).text()).contains("entregue");
    }

    @Test
    @DisplayName("cancelar (em_preparo → cancelado) → terminal + notificação de cancelamento")
    void cancel_notifies() {
        ComidaOrder order = seedOrder();
        service.updateStatus(COMPANY, order.id(), "em_preparo", null);   // aceite primeiro (1 envio).
        fakeEvolution.reset();
        ComidaOrder cancelled = service.updateStatus(COMPANY, order.id(), "cancelado", null);
        assertThat(cancelled.status()).isEqualTo("cancelado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelado");
    }

    // -------------------------------------------------------------------------
    // ONDA 1 do backlog: cupom (#1), fidelidade (#2) e taxa por zona (#8) na criação.
    // -------------------------------------------------------------------------

    private UUID seedMenuItem(String name, int price) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into comida_menu_items (id, company_id, name, price_cents, category, available) "
                + "values (?, ?, ?, ?, 'lanches', true)",
            id, COMPANY, name, price);
        return id;
    }

    private void seedCoupon(String code, String kind, int value, int minOrder) {
        jdbcTemplate.update(
            "insert into comida_coupons (company_id, code, kind, value, min_order_cents) values (?, ?, ?, ?, ?)",
            COMPANY, code, kind, value, minOrder);
    }

    @Test
    @DisplayName("cupom válido aplica desconto + incrementa uses; inválido é SILENCIOSO (sem desconto)")
    void coupon_appliedOrSilentlyIgnored() {
        UUID item = seedMenuItem("X-Salada", 2000);
        seedCoupon("DEZ", "percent", 10, 0);

        ComidaOrder withCoupon = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item, 2, List.of())), "dez", null, null);   // case-insensitive
        assertThat(withCoupon.discountCents()).isEqualTo(400);
        assertThat(withCoupon.totalCents()).isEqualTo(3600);
        assertThat(withCoupon.couponCodeSnapshot()).isEqualTo("DEZ");
        assertThat(jdbcTemplate.queryForObject(
            "select uses from comida_coupons where company_id = ? and code = 'DEZ'",
            Integer.class, COMPANY)).isEqualTo(1);

        ComidaOrder invalid = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item, 1, List.of())), "NAOEXISTE", null, null);
        assertThat(invalid.discountCents()).isZero();
        assertThat(invalid.couponCodeSnapshot()).isNull();
    }

    @Test
    @DisplayName("fidelidade: no pedido seguinte ao N-ésimo ENTREGUE, desconto automático + loyalty_applied")
    void loyalty_appliedOnThreshold() {
        jdbcTemplate.update(
            "update comida_loyalty_config set enabled = true, threshold_orders = 2, reward_kind = 'percent', "
                + "reward_value = 50 where company_id = ?", COMPANY);
        jdbcTemplate.update(
            "insert into comida_loyalty_config (company_id, enabled, threshold_orders, reward_kind, reward_value) "
                + "select ?, true, 2, 'percent', 50 where not exists "
                + "(select 1 from comida_loyalty_config where company_id = ?)", COMPANY, COMPANY);
        UUID item = seedMenuItem("Prato", 3000);

        // 2 pedidos ENTREGUES no histórico do contato.
        for (int i = 0; i < 2; i++) {
            ComidaOrder o = service.create(COMPANY, conversationId, contactId, "Rua X 1",
                List.of(new OrderLineInput(item, 1, List.of())), null, null, null);
            jdbcTemplate.update("update comida_orders set status = 'entregue' where id = ?", o.id());
        }

        ComidaOrder third = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item, 1, List.of())), null, null, null);
        assertThat(third.loyaltyApplied()).isTrue();
        assertThat(third.discountCents()).isEqualTo(1500);
        assertThat(third.totalCents()).isEqualTo(1500);
    }

    @Test
    @DisplayName("zona ativa resolve a taxa + snapshot do nome; zona inválida/inativa → taxa flat")
    void zone_feeResolution() {
        jdbcTemplate.update(
            "insert into comida_config (company_id, delivery_fee_cents, min_order_cents) values (?, 500, 0) "
                + "on conflict (company_id) do update set delivery_fee_cents = 500", COMPANY);
        UUID zone = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into comida_delivery_zones (id, company_id, name, fee_cents) values (?, ?, 'Centro', 900)",
            zone, COMPANY);
        UUID inactive = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into comida_delivery_zones (id, company_id, name, fee_cents, active) values (?, ?, 'Longe', 1500, false)",
            inactive, COMPANY);
        UUID item = seedMenuItem("Combo", 4000);

        ComidaOrder zoned = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item, 1, List.of())), null, zone, null);
        assertThat(zoned.deliveryFeeCents()).isEqualTo(900);
        assertThat(zoned.zoneNameSnapshot()).isEqualTo("Centro");
        assertThat(zoned.totalCents()).isEqualTo(4900);

        ComidaOrder fallback = service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item, 1, List.of())), null, inactive, null);
        assertThat(fallback.deliveryFeeCents()).isEqualTo(500);
        assertThat(fallback.zoneNameSnapshot()).isNull();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-comida";
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
