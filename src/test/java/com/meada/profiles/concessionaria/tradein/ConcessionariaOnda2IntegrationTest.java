package com.meada.profiles.concessionaria.tradein;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.concessionaria.leads.ConcessionariaLeadService;
import com.meada.profiles.concessionaria.reminders.ConcessionariaReminderJob;
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
 * Integration test da onda Concessionária 2 (backlog #5/#7/#12): trade-in coletado pela IA
 * (avaliação humana), pós-venda no lead fechado (review link, toggle) e revisão programada
 * (opt-in OFF, 1 toque por lead). EvolutionSender é um FAKE.
 */
@Import(ConcessionariaOnda2IntegrationTest.TestConfig.class)
class ConcessionariaOnda2IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000115");
    private static final UUID INSTANCE = UUID.fromString("d1100000-0000-0000-0000-000000000115");

    @Autowired
    private TrocaCarroHandler trocaHandler;
    @Autowired
    private TradeInService tradeInService;
    @Autowired
    private ConcessionariaLeadService leadService;
    @Autowired
    private ConcessionariaReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID vehicleId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'concessionaria')",
            COMPANY, "Conc Onda2", "conc-onda2");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cc2", "tok-cc2");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Tico')",
            contactId, COMPANY, "+5511999990411");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        vehicleId = jdbcTemplate.queryForObject(
            "insert into concessionaria_vehicles (company_id, brand, model, model_year, price_cents) "
                + "values (?, 'Toyota', 'Corolla', 2023, 12000000) returning id",
            UUID.class, COMPANY);
    }

    @Test
    @DisplayName("trade-in: tag coleta o usado (valor declarado ≠ avaliação); gestão humana avalia")
    void tradeIn() {
        String tag = "Anotado!\n<troca_carro>{\"brand\":\"Fiat\",\"model\":\"Argo\",\"year\":2019,"
            + "\"km\":58000,\"condition\":\"bom estado\",\"asking_cents\":4500000,"
            + "\"interest_vehicle_id\":\"" + vehicleId + "\"}</troca_carro>";
        trocaHandler.parseAndOpen(COMPANY, conversationId, contactId, tag);

        var list = tradeInService.list(COMPANY, "aberta");
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("usedModel")).isEqualTo("Argo");
        assertThat(list.get(0).get("askingCents")).isEqualTo(4500000);
        assertThat(list.get(0).get("interestVehicle")).isEqualTo("Toyota Corolla");

        UUID id = (UUID) list.get(0).get("id");
        assertThat(tradeInService.update(COMPANY, id, "avaliada", 4000000, "pneu gasto")).isTrue();
        assertThat(tradeInService.list(COMPANY, "avaliada")).hasSize(1);
    }

    @Test
    @DisplayName("pós-venda: lead fechado → parabéns + review link; toggle off silencia")
    void postSale() {
        jdbcTemplate.update(
            "insert into concessionaria_config (company_id, review_link) values (?, 'https://g.page/r/conc')",
            COMPANY);
        UUID lead = seedLead("em_negociacao");
        leadService.updateStatus(COMPANY, lead, "fechado", null);
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("Parabéns pelo carro novo") && t.contains("https://g.page/r/conc"));

        jdbcTemplate.update("update concessionaria_config set post_sale_enabled = false where company_id = ?",
            COMPANY);
        UUID lead2 = seedLead("em_negociacao");
        fakeEvolution.reset();
        leadService.updateStatus(COMPANY, lead2, "fechado", null);
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("revisão programada: OFF por default; ON → lead fechado há N meses recebe 1 toque")
    void serviceReminder() {
        UUID lead = seedLead("fechado");
        jdbcTemplate.update(
            "update concessionaria_leads set status_updated_at = now() - interval '400 days' where id = ?",
            lead);

        assertThat(job.runServiceReminders()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into concessionaria_config (company_id, service_reminder_enabled, service_reminder_months) "
                + "values (?, true, 12) on conflict (company_id) do update set "
                + "service_reminder_enabled = true, service_reminder_months = 12", COMPANY);
        assertThat(job.runServiceReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Corolla").contains("revisão");

        fakeEvolution.reset();
        assertThat(job.runServiceReminders()).isZero();   // 1 toque por lead.
    }

    private UUID seedLead(String status) {
        return jdbcTemplate.queryForObject(
            "insert into concessionaria_leads (company_id, vehicle_id, conversation_id, contact_id, "
                + "customer_name, vehicle_brand, vehicle_model, vehicle_year, vehicle_price_cents, "
                + "payment_condition, status) "
                + "values (?, ?, ?, ?, 'Tico', 'Toyota', 'Corolla', 2023, 12000000, 'avista', ?) returning id",
            UUID.class, COMPANY, vehicleId, conversationId, contactId, status);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-conc-onda2";
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
