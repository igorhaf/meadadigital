package com.meada.whatsapp.profiles.comida.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuItem;
import com.meada.whatsapp.profiles.comida.menu.ComidaMenuService;
import com.meada.whatsapp.profiles.comida.orders.ComidaOrderService.InvalidStatusTransitionException;
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
            List.of(new OrderLineInput(item.id(), 2, List.of())), null);
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
