package com.meada.whatsapp.profiles.casamento.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
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
 * Testa o AprovacaoCasamentoHandler (camada 8.7): aprovação de uma proposta em 'orcada' muta o estado
 * (gate de aprovação em 2 fases — clone do AprovacaoPropostaHandler do Eventos); recusada vira
 * recusada; tag em proposta que NÃO está orcada (rascunho) → empty + estado intacto; sem tag → empty.
 * EvolutionSender fake (notifica aprovada).
 */
@Import(AprovacaoCasamentoHandlerTest.TestConfig.class)
class AprovacaoCasamentoHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AprovacaoCasamentoHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento H2", "casamento-h2");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990192", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private UUID seedProposal(String status) {
        UUID proposalId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into wedding_proposals (id, company_id, contact_id, conversation_id, customer_name, "
                + "wedding_style, total_cents, status) "
                + "values (?, ?, ?, ?, 'Marina', 'clássico', 800000, ?)",
            proposalId, COMPANY, contactId, conversationId, status);
        jdbcTemplate.update(
            "insert into wedding_proposal_items (company_id, proposal_id, description, quantity, unit_price_cents, line_total_cents) "
                + "values (?, ?, 'Espaço', 1, 800000, 800000)",
            COMPANY, proposalId);
        return proposalId;
    }

    @Test
    @DisplayName("decisao 'aprovada' em proposta orcada → status vira aprovada")
    void parseAndApply_approveOrcada() {
        UUID proposalId = seedProposal("orcada");
        String aiText = "Que ótimo! Registrei sua aprovação.\n"
            + "<aprovacao_casamento>{\"proposal_id\":\"" + proposalId + "\",\"decisao\":\"aprovada\"}</aprovacao_casamento>";

        Optional<WeddingProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("aprovada");
        String status = jdbcTemplate.queryForObject("select status from wedding_proposals where id = ?", String.class, proposalId);
        assertThat(status).isEqualTo("aprovada");
    }

    @Test
    @DisplayName("decisao 'recusada' em proposta orcada → status vira recusada")
    void parseAndApply_refuseOrcada() {
        UUID proposalId = seedProposal("orcada");
        String aiText = "Tudo bem, registrei.\n"
            + "<aprovacao_casamento>{\"proposal_id\":\"" + proposalId + "\",\"decisao\":\"recusada\"}</aprovacao_casamento>";

        Optional<WeddingProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("recusada");
        String status = jdbcTemplate.queryForObject("select status from wedding_proposals where id = ?", String.class, proposalId);
        assertThat(status).isEqualTo("recusada");
    }

    @Test
    @DisplayName("tag em proposta que NÃO está orcada (rascunho) → Optional.empty + estado intacto")
    void parseAndApply_notOrcada() {
        UUID proposalId = seedProposal("rascunho");
        String aiText = "Aprovado!\n<aprovacao_casamento>{\"proposal_id\":\"" + proposalId
            + "\",\"decisao\":\"aprovada\"}</aprovacao_casamento>";

        Optional<WeddingProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);

        assertThat(o).isEmpty();
        String status = jdbcTemplate.queryForObject("select status from wedding_proposals where id = ?", String.class, proposalId);
        assertThat(status).isEqualTo("rascunho");
    }

    @Test
    @DisplayName("sem tag → Optional.empty")
    void parseAndApply_noTag() {
        Optional<WeddingProposal> o = handler.parseAndApply(COMPANY, conversationId,
            "Claro, qualquer dúvida estou à disposição!");
        assertThat(o).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-casamento";
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
