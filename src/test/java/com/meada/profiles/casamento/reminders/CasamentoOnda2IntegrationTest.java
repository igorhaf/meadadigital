package com.meada.profiles.casamento.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.casamento.proposals.WeddingProposalService;
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
 * Integration test da onda Casamento 2 (backlog #6/#8): pós-casamento (agradecimento + review
 * link no auge da emoção, toggle) e follow-up de orçamento parado (1 toque por episódio,
 * re-orçar rearma). EvolutionSender é um FAKE.
 */
@Import(CasamentoOnda2IntegrationTest.TestConfig.class)
class CasamentoOnda2IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("b8000000-0000-0000-0000-000000000113");
    private static final UUID INSTANCE = UUID.fromString("b8100000-0000-0000-0000-000000000113");

    @Autowired
    private WeddingProposalService proposalService;
    @Autowired
    private WeddingAutoTransitionJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento Onda2", "casamento-onda2");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cs2", "tok-cs2");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Ana & João')",
            contactId, COMPANY, "+5511999990391");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
    }

    private UUID seedProposal(String status) {
        return jdbcTemplate.queryForObject(
            "insert into wedding_proposals (company_id, contact_id, conversation_id, customer_name, "
                + "total_cents, status) values (?, ?, ?, 'Ana & João', 2000000, ?) returning id",
            UUID.class, COMPANY, contactId, conversationId, status);
    }

    @Test
    @DisplayName("pós-casamento: realizada → depoimento com review link; toggle off silencia")
    void postEvent() {
        jdbcTemplate.update(
            "insert into wedding_config (company_id, review_link) values (?, 'https://g.page/r/wed')",
            COMPANY);
        UUID fechada = seedProposal("fechada");
        proposalService.updateStatus(COMPANY, fechada, "realizada");
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("https://g.page/r/wed") && t.contains("indicação"));

        jdbcTemplate.update("update wedding_config set post_event_enabled = false where company_id = ?",
            COMPANY);
        UUID fechada2 = seedProposal("fechada");
        fakeEvolution.reset();
        proposalService.updateStatus(COMPANY, fechada2, "realizada");
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .noneMatch(t -> t.contains("indicação"));
    }

    @Test
    @DisplayName("follow-up: orcada parada além da janela → 1 toque por episódio; re-orçar rearma")
    void followUp() {
        UUID parada = seedProposal("orcada");
        jdbcTemplate.update(
            "update wedding_proposals set status_updated_at = now() - interval '7 days' where id = ?",
            parada);
        seedProposal("orcada");   // recente — não dispara.

        assertThat(job.runFollowUps()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("orçamento do casamento");

        fakeEvolution.reset();
        assertThat(job.runFollowUps()).isZero();   // 1 por episódio.

        // novo episódio rearma.
        jdbcTemplate.update(
            "update wedding_proposals set status_updated_at = now() - interval '6 days', "
                + "follow_up_sent_at = now() - interval '8 days' where id = ?", parada);
        assertThat(job.runFollowUps()).isEqualTo(1);

        // toggle off.
        jdbcTemplate.update(
            "insert into wedding_config (company_id, follow_up_enabled) values (?, false) "
                + "on conflict (company_id) do update set follow_up_enabled = false", COMPANY);
        jdbcTemplate.update("update wedding_proposals set follow_up_sent_at = null where id = ?", parada);
        fakeEvolution.reset();
        assertThat(job.runFollowUps()).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-casamento-onda2";
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
