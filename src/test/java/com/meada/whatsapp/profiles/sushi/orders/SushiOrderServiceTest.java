package com.meada.whatsapp.profiles.sushi.orders;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuItem;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuService;
import com.meada.whatsapp.profiles.sushi.orders.SushiOrderService.InvalidStatusTransitionException;
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
 * Testa o SushiOrderService (camada 7.1): transição válida, transição inválida 409, notificação
 * outbound disparada. EvolutionSender é um fake que registra os envios.
 */
@Import(SushiOrderServiceTest.TestConfig.class)
class SushiOrderServiceTest extends AbstractIntegrationTest {

    @Autowired
    private SushiOrderService service;
    @Autowired
    private SushiMenuService menuService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c7000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d7000000-0000-0000-0000-000000000001");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'sushi')",
            COMPANY, "Sushi O", "sushi-o");
        // USER em users (FK audit_log_user_id_fkey) — ver nota no SushiMenuServiceTest.
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sushi-o.dev', 'admin')",
            USER, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990003", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private SushiOrder seedOrder() {
        SushiMenuItem item = menuService.create(COMPANY, USER, "Filadélfia", null, 3200, "hot_rolls");
        return service.create(COMPANY, conversationId, contactId, "Rua X 1",
            List.of(new OrderLineInput(item.id(), 2)), null);
    }

    @Test
    @DisplayName("transição válida (recebido → preparo) → status atualiza + notificação enviada")
    void validTransition_notifies() {
        SushiOrder order = seedOrder();
        assertThat(order.status()).isEqualTo("recebido");

        SushiOrder updated = service.updateStatus(COMPANY, order.id(), "preparo");
        assertThat(updated.status()).isEqualTo("preparo");

        // Notificação outbound do status 'preparo' foi enviada com o texto fixo.
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("entrou em preparo");
    }

    @Test
    @DisplayName("transição inválida (recebido → entregue) → InvalidStatusTransitionException (409)")
    void invalidTransition() {
        SushiOrder order = seedOrder();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, order.id(), "entregue"))
            .isInstanceOf(InvalidStatusTransitionException.class);
        // status não mudou, nada enviado.
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("cancelar (recebido → cancelado) → status terminal + notificação de cancelamento")
    void cancel_notifies() {
        SushiOrder order = seedOrder();
        SushiOrder cancelled = service.updateStatus(COMPANY, order.id(), "cancelado");
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
            return "key-sushi";
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
