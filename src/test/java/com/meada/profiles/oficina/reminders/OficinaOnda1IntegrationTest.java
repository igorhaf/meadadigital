package com.meada.profiles.oficina.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.oficina.catalog.OficinaCatalogItem;
import com.meada.profiles.oficina.catalog.OficinaCatalogService;
import com.meada.profiles.oficina.orders.ServiceOrder;
import com.meada.profiles.oficina.orders.ServiceOrderService;
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
 * Integration test da onda Oficina 1 (backlog #1/#2): catálogo tabelado pré-preenchendo a OS na
 * abertura (preço do catálogo, best-effort) e lembrete de retorno pós-entrega via
 * {@link OficinaReminderJob}. EvolutionSender é um FAKE.
 */
@Import(OficinaOnda1IntegrationTest.TestConfig.class)
class OficinaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("af000000-0000-0000-0000-0000000000d2");
    private static final UUID INSTANCE = UUID.fromString("af100000-0000-0000-0000-0000000000d2");
    private static final UUID USER = UUID.fromString("af200000-0000-0000-0000-0000000000d2");

    @Autowired
    private ServiceOrderService orderService;
    @Autowired
    private OficinaCatalogService catalogService;
    @Autowired
    private OficinaReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID vehicleId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')",
            COMPANY, "Oficina Onda", "oficina-onda");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@ofc-onda.dev', 'admin')",
            USER, COMPANY);
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-ofc", "tok-ofc");
        UUID contact = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Nando')",
            contact, COMPANY, "+5511999990261");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contact, INSTANCE);
        vehicleId = jdbcTemplate.queryForObject(
            "insert into os_vehicles (company_id, contact_id, plate, model) "
                + "values (?, ?, 'ABC1D23', 'Gol 1.6') returning id",
            UUID.class, COMPANY, contact);
    }

    @Test
    @DisplayName("abertura com serviços TABELADOS pré-preenche itens com preço do catálogo (best-effort)")
    void openWithCatalogItems() {
        OficinaCatalogItem oleo = catalogService.create(COMPANY, USER, "Troca de óleo", "mão de obra", 12000, true, null);
        OficinaCatalogItem inativo = catalogService.create(COMPANY, USER, "Alinhamento", null, 9000, false, null);

        ServiceOrder os = orderService.openWithCatalogItems(COMPANY, vehicleId, null, conversationId,
            "barulho na suspensão", null, null, null, List.of(
                new ServiceOrderService.CatalogLine(oleo.id(), 1),
                new ServiceOrderService.CatalogLine(inativo.id(), 1),      // inativo → ignorado
                new ServiceOrderService.CatalogLine(UUID.randomUUID(), 1)  // inexistente → ignorado
            ));

        assertThat(os.status()).isEqualTo("aberta");
        assertThat(os.totalCents()).isEqualTo(12000);   // só o tabelado ativo entrou.
        assertThat(os.items()).hasSize(1);
        assertThat(os.items().get(0).description()).isEqualTo("Troca de óleo");
    }

    @Test
    @DisplayName("entrega materializa next_return_date; retorno vencido → lembrete 1x; toggle off → nada")
    void returnReminder() {
        ServiceOrder os = orderService.open(COMPANY, vehicleId, null, conversationId,
            "revisão", null, null, null);
        orderService.addItem(COMPANY, os.id(), "mao_de_obra", "Revisão", 1, 30000);
        orderService.updateStatus(COMPANY, os.id(), "orcada");
        orderService.updateStatus(COMPANY, os.id(), "aprovada");
        orderService.updateStatus(COMPANY, os.id(), "em_execucao");
        orderService.updateStatus(COMPANY, os.id(), "concluida");
        orderService.updateStatus(COMPANY, os.id(), "entregue");

        java.sql.Date next = jdbcTemplate.queryForObject(
            "select next_return_date from service_orders where id = ?", java.sql.Date.class, os.id());
        assertThat(next).isNotNull();   // materializado na entrega (hoje + 180 default).

        // vence o retorno "no passado" pra disparar já.
        jdbcTemplate.update("update service_orders set next_return_date = current_date - 1 where id = ?",
            os.id());
        fakeEvolution.reset();
        assertThat(job.runReturnReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Gol 1.6");

        // 1x por OS.
        fakeEvolution.reset();
        assertThat(job.runReturnReminders()).isZero();

        // toggle off: outra OS vencida não dispara.
        jdbcTemplate.update(
            "insert into os_config (company_id, return_reminder_enabled) values (?, false)", COMPANY);
        jdbcTemplate.update("update service_orders set return_reminded_at = null where id = ?", os.id());
        assertThat(job.runReturnReminders()).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-oficina-reminder";
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
