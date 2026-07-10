package com.meada.profiles.eventos.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.eventos.proposals.EventProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Eventos 1 (backlog #3/#6/#7/#8): aviso de data ocupada,
 * auto-transição fechada→realizada com pós-venda (review link), e follow-up de orçamento parado
 * (1 toque por episódio). EvolutionSender é um FAKE. O catálogo (#2/#9) vive no contexto da IA.
 */
@Import(EventosOnda1IntegrationTest.TestConfig.class)
class EventosOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("b2000000-0000-0000-0000-000000000107");
    private static final UUID INSTANCE = UUID.fromString("b2100000-0000-0000-0000-000000000107");

    @Autowired
    private EventosReminderJob job;
    @Autowired
    private EventProposalService proposalService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'eventos')",
            COMPANY, "Buffet Onda", "buffet-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-evo", "tok-evo");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Duda')",
            contactId, COMPANY, "+5511999990331");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
    }

    private UUID seedProposal(String status, LocalDate eventDate) {
        return jdbcTemplate.queryForObject(
            "insert into event_proposals (company_id, contact_id, conversation_id, customer_name, "
                + "event_type, event_date, total_cents, status) "
                + "values (?, ?, ?, 'Duda', 'aniversário', ?, 500000, ?) returning id",
            UUID.class, COMPANY, contactId, conversationId,
            eventDate == null ? null : Date.valueOf(eventDate), status);
    }

    @Test
    @DisplayName("data ocupada: aprovada/fechada/realizada contam; rascunho/orcada não; excludeId funciona")
    void dateCheck() {
        LocalDate data = LocalDate.now().plusDays(30);
        UUID fechada = seedProposal("fechada", data);
        seedProposal("orcada", data);   // não conta.

        assertThat(proposalService.countOccupied(COMPANY, data, null)).isEqualTo(1);
        assertThat(proposalService.countOccupied(COMPANY, data, fechada)).isZero();
        assertThat(proposalService.countOccupied(COMPANY, data.plusDays(1), null)).isZero();
    }

    @Test
    @DisplayName("fechada com data passada → realizada + pós-venda com review link; toggle off silencia")
    void autoCompleteAndPostEvent() {
        jdbcTemplate.update(
            "insert into event_config (company_id, review_link) values (?, 'https://g.page/r/buffet')",
            COMPANY);
        UUID passada = seedProposal("fechada", LocalDate.now().minusDays(1));
        UUID futura = seedProposal("fechada", LocalDate.now().plusDays(10));

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(passada)).isEqualTo("realizada");
        assertThat(statusOf(futura)).isEqualTo("fechada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text())
            .contains("inesquecível").contains("https://g.page/r/buffet").contains("indicação");

        // idempotente (status já mudou).
        fakeEvolution.reset();
        assertThat(job.runAutoComplete()).isZero();

        // toggle auto_complete off: outra proposta vencida não transita.
        jdbcTemplate.update("update event_config set auto_complete_enabled = false where company_id = ?",
            COMPANY);
        UUID outra = seedProposal("fechada", LocalDate.now().minusDays(2));
        assertThat(job.runAutoComplete()).isZero();
        assertThat(statusOf(outra)).isEqualTo("fechada");
    }

    @Test
    @DisplayName("orcada parada além da janela → follow-up 1x por episódio; re-orçar rearma")
    void followUp() {
        UUID parada = seedProposal("orcada", LocalDate.now().plusDays(60));
        jdbcTemplate.update(
            "update event_proposals set status_updated_at = now() - interval '5 days' where id = ?", parada);
        seedProposal("orcada", LocalDate.now().plusDays(60));   // recente — não dispara.

        assertThat(job.runFollowUps()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("orçamento").contains("aniversário");

        // 1 por episódio.
        fakeEvolution.reset();
        assertThat(job.runFollowUps()).isZero();

        // novo episódio (status_updated_at > marker) rearma.
        jdbcTemplate.update(
            "update event_proposals set status_updated_at = now() - interval '4 days', "
                + "follow_up_sent_at = now() - interval '6 days' where id = ?", parada);
        assertThat(job.runFollowUps()).isEqualTo(1);

        // toggle off silencia.
        jdbcTemplate.update(
            "insert into event_config (company_id, follow_up_enabled) values (?, false) "
                + "on conflict (company_id) do update set follow_up_enabled = false", COMPANY);
        jdbcTemplate.update("update event_proposals set follow_up_sent_at = null where id = ?", parada);
        fakeEvolution.reset();
        assertThat(job.runFollowUps()).isZero();
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from event_proposals where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-eventos-onda";
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
