package com.meada.whatsapp.profiles.viagens.proposals;

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
 * Testa o AprovacaoViagemHandler (camada 8.18 / perfil viagens): aprovação de uma proposta em 'orcada'
 * muta o estado (gate de aprovação em 2 fases — clone do AprovacaoPropostaHandler); recusada → recusada;
 * tag em proposta que NÃO está orcada → empty + estado intacto; proposta inexistente → empty; sem tag →
 * empty. EvolutionSender fake (notifica aprovada). Espelho do AprovacaoPropostaHandlerTest (chassi
 * eventos 8.2).
 */
@Import(AprovacaoViagemHandlerTest.TestConfig.class)
class AprovacaoViagemHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AprovacaoViagemHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ce100000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'viagens')",
            COMPANY, "Viagens H2", "viagens-h2");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999991292", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private UUID seedProposal(String status) {
        UUID proposalId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into travel_proposals (id, company_id, contact_id, conversation_id, customer_name, "
                + "destination, total_cents, status) "
                + "values (?, ?, ?, ?, 'Marina', 'Paris', 800000, ?)",
            proposalId, COMPANY, contactId, conversationId, status);
        jdbcTemplate.update(
            "insert into travel_proposal_items (company_id, proposal_id, category, description, quantity, unit_price_cents, line_total_cents) "
                + "values (?, ?, 'aereo', 'Passagens', 1, 800000, 800000)",
            COMPANY, proposalId);
        return proposalId;
    }

    @Test
    @DisplayName("decisao 'aprovada' em proposta orcada → status vira aprovada")
    void parseAndApply_approveOrcada() {
        UUID proposalId = seedProposal("orcada");
        String aiText = "Que ótimo! Registrei sua aprovação.\n"
            + "<aprovacao_viagem>{\"proposal_id\":\"" + proposalId + "\",\"decisao\":\"aprovada\"}</aprovacao_viagem>";

        Optional<TravelProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("aprovada");
        String status = jdbcTemplate.queryForObject("select status from travel_proposals where id = ?", String.class, proposalId);
        assertThat(status).isEqualTo("aprovada");
    }

    @Test
    @DisplayName("decisao 'recusada' em proposta orcada → status vira recusada")
    void parseAndApply_refuseOrcada() {
        UUID proposalId = seedProposal("orcada");
        String aiText = "Tudo bem!\n<aprovacao_viagem>{\"proposal_id\":\"" + proposalId
            + "\",\"decisao\":\"recusada\"}</aprovacao_viagem>";

        Optional<TravelProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);

        assertThat(o).isPresent();
        assertThat(o.get().status()).isEqualTo("recusada");
    }

    @Test
    @DisplayName("tag em proposta que NÃO está orcada (rascunho) → Optional.empty + estado intacto")
    void parseAndApply_notOrcada() {
        UUID proposalId = seedProposal("rascunho");
        String aiText = "Aprovado!\n<aprovacao_viagem>{\"proposal_id\":\"" + proposalId
            + "\",\"decisao\":\"aprovada\"}</aprovacao_viagem>";

        Optional<TravelProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);

        assertThat(o).isEmpty();
        String status = jdbcTemplate.queryForObject("select status from travel_proposals where id = ?", String.class, proposalId);
        assertThat(status).isEqualTo("rascunho");
    }

    @Test
    @DisplayName("proposal_id inexistente → Optional.empty")
    void parseAndApply_unknownProposal() {
        String aiText = "Aprovado!\n<aprovacao_viagem>{\"proposal_id\":\"" + UUID.randomUUID()
            + "\",\"decisao\":\"aprovada\"}</aprovacao_viagem>";
        Optional<TravelProposal> o = handler.parseAndApply(COMPANY, conversationId, aiText);
        assertThat(o).isEmpty();
    }

    @Test
    @DisplayName("sem tag → Optional.empty")
    void parseAndApply_noTag() {
        Optional<TravelProposal> o = handler.parseAndApply(COMPANY, conversationId, "Sem tag aqui.");
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
            return "key-viagens";
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
