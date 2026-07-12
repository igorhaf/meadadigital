package com.meada.profiles.oficina.orders;

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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AprovacaoOsHandler (camada 7.9): aprovação de uma OS em 'orcada' muta o estado (NOVIDADE da
 * SM: a IA altera estado de artefato existente); tag em OS que NÃO está orcada → empty + estado
 * intacto; OS inexistente → empty; decisão inválida → empty. EvolutionSender fake (notifica aprovada).
 */
@Import(AprovacaoOsHandlerTest.TestConfig.class)
class AprovacaoOsHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AprovacaoOsHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;
    private UUID vehicleId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            COMPANY, "Oficina AP", "oficina-ap");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990196", "João");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        vehicleId = UUID.randomUUID();
        jdbcTemplate.update("insert into os_vehicles (id, company_id, contact_id, plate, brand, model) "
            + "values (?, ?, ?, 'ABC1D23', 'Fiat', 'Uno')", vehicleId, COMPANY, contactId);
    }

    private UUID seedOrder(String status) {
        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into service_orders (id, company_id, contact_id, vehicle_id, conversation_id, customer_name, "
                + "vehicle_plate, vehicle_model, complaint, total_cents, status) "
                + "values (?, ?, ?, ?, ?, 'João', 'ABC1D23', 'Uno', 'Barulho no motor', 18000, ?)",
            orderId, COMPANY, contactId, vehicleId, conversationId, status);
        jdbcTemplate.update(
            "insert into os_items (company_id, service_order_id, kind, description, quantity, unit_price_cents, line_total_cents) "
                + "values (?, ?, 'mao_de_obra', 'Mão de obra', 1, 18000, 18000)",
            COMPANY, orderId);
        return orderId;
    }

    @Test
    @DisplayName("decisao 'aprovada' em OS orcada → status vira aprovada")
    void parseAndApply_approveOrcada() {
        UUID orderId = seedOrder("orcada");
        String aiText = "Perfeito! Registrei sua aprovação.\n"
            + "<aprovacao_os>{\"service_order_id\":\"" + orderId + "\",\"decisao\":\"aprovada\"}</aprovacao_os>";

        Optional<ServiceOrder> o = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("aprovada");
        String status = jdbcTemplate.queryForObject("select status from service_orders where id = ?", String.class, orderId);
        assertThat(status).isEqualTo("aprovada");
    }

    @Test
    @DisplayName("tag em OS que NÃO está orcada (aberta) → Optional.empty + estado intacto")
    void parseAndApply_notOrcada() {
        UUID orderId = seedOrder("aberta");
        String aiText = "Aprovado!\n<aprovacao_os>{\"service_order_id\":\"" + orderId
            + "\",\"decisao\":\"aprovada\"}</aprovacao_os>";

        Optional<ServiceOrder> o = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isEmpty();
        String status = jdbcTemplate.queryForObject("select status from service_orders where id = ?", String.class, orderId);
        assertThat(status).isEqualTo("aberta");
    }

    @Test
    @DisplayName("service_order_id inexistente → Optional.empty")
    void parseAndApply_unknownOrder() {
        String aiText = "Aprovado!\n<aprovacao_os>{\"service_order_id\":\"" + UUID.randomUUID()
            + "\",\"decisao\":\"aprovada\"}</aprovacao_os>";
        Optional<ServiceOrder> o = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);
        assertThat(o).isEmpty();
    }

    @Test
    @DisplayName("decisao inválida ('xpto') → Optional.empty + estado intacto")
    void parseAndApply_invalidDecision() {
        UUID orderId = seedOrder("orcada");
        String aiText = "Ok!\n<aprovacao_os>{\"service_order_id\":\"" + orderId
            + "\",\"decisao\":\"xpto\"}</aprovacao_os>";

        Optional<ServiceOrder> o = handler.parseAndApply(COMPANY, conversationId, contactId, aiText);

        assertThat(o).isEmpty();
        String status = jdbcTemplate.queryForObject("select status from service_orders where id = ?", String.class, orderId);
        assertThat(status).isEqualTo("orcada");
    }

    @Test
    @DisplayName("BARREIRA DE CONTATO: aprovação vinda de OUTRO contato → Optional.empty + estado intacto")
    void parseAndApply_contactBarrier() {
        UUID orderId = seedOrder("orcada");
        UUID otherContact = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            otherContact, COMPANY, "+5511999990197", "Outro Cliente");
        String aiText = "Aprovado!\n<aprovacao_os>{\"service_order_id\":\"" + orderId
            + "\",\"decisao\":\"aprovada\"}</aprovacao_os>";

        // conversa de OUTRO contato tentando aprovar a OS do João → bloqueada, OS intacta.
        Optional<ServiceOrder> o = handler.parseAndApply(COMPANY, conversationId, otherContact, aiText);

        assertThat(o).isEmpty();
        String status = jdbcTemplate.queryForObject("select status from service_orders where id = ?", String.class, orderId);
        assertThat(status).isEqualTo("orcada");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-oficina";
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
