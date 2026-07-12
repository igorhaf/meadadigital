package com.meada.profiles.eventos.proposals;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.eventos.proposals.EventProposalService.EmptyBudgetException;
import com.meada.profiles.eventos.proposals.EventProposalService.InvalidStatusTransitionException;
import com.meada.profiles.eventos.proposals.EventProposalService.ProposalLockedException;
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
 * Testa o EventProposalService (camada 8.2): abre proposta + snapshots; add item de orçamento
 * recalcula total; orçar sem item → empty_budget; mutar item em proposta travada → proposal_locked;
 * transição inválida → 409; notifica em orcada com total. FakeEvolutionSender (notifica orcada).
 */
@Import(EventProposalServiceTest.TestConfig.class)
class EventProposalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EventProposalService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000002");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'eventos')",
            COMPANY, "Eventos Svc", "eventos-svc");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990280", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("open tira snapshot do contact (nome/telefone) e abre em rascunho com total 0")
    void open_snapshotsContact() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, 120, "Festa grande", null);
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina");
        assertThat(p.customerPhone()).isEqualTo("+5511999990280");
        assertThat(p.eventType()).isEqualTo("casamento");
        assertThat(p.guestCount()).isEqualTo(120);
    }

    @Test
    @DisplayName("addItem recalcula total_cents materializado a cada item de orçamento")
    void addItem_recalcsTotal() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, null, "Briefing", null);
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        service.addItem(COMPANY, p.id(), "Buffet", 1, 300000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(800000);
    }

    @Test
    @DisplayName("updateItem parcial (só quantity / só preço) materializa line_total com os valores FINAIS")
    void updateItem_partial_materializesFinalLineTotal() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, null, "Briefing", null);
        EventProposalItem item = service.addItem(COMPANY, p.id(), "Espaço", 2, 10000);
        assertThat(item.lineTotalCents()).isEqualTo(20000);

        // Muda SÓ a quantidade → 3 × 10000 (unit mantido) = 30000. Regressão real: a SET clause
        // referenciando quantity * unit_price_cents lia os valores ANTIGOS da linha e gravava 20000.
        EventProposalItem updated = service.updateItem(COMPANY, p.id(), item.id(), null, 3, null);
        assertThat(updated.lineTotalCents()).isEqualTo(30000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(30000);

        // Muda SÓ o preço unitário → 3 × 20000 = 60000.
        EventProposalItem repriced = service.updateItem(COMPANY, p.id(), item.id(), null, null, 20000);
        assertThat(repriced.lineTotalCents()).isEqualTo(60000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(60000);
    }

    @Test
    @DisplayName("orçar proposta SEM item de orçamento → EmptyBudgetException")
    void orcar_emptyBudget() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, null, "Briefing", null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "orcada"))
            .isInstanceOf(EmptyBudgetException.class);
    }

    @Test
    @DisplayName("rascunho→orcada com total>0 → notifica o cliente com o total formatado")
    void orcar_notifiesWithTotal() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, null, "Briefing", null);
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        EventProposal orcada = service.updateStatus(COMPANY, p.id(), "orcada");
        assertThat(orcada.status()).isEqualTo("orcada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text())
            .contains("R$ 5000,00")
            .contains("casamento");
    }

    @Test
    @DisplayName("mutar item de orçamento numa proposta travada (fechada) → ProposalLockedException")
    void addItem_lockedProposal() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, null, "Briefing", null);
        // força o estado 'fechada' direto no banco (travado).
        jdbcTemplate.update("update event_proposals set status = 'fechada' where id = ?", p.id());
        assertThatThrownBy(() -> service.addItem(COMPANY, p.id(), "Extra", 1, 1000))
            .isInstanceOf(ProposalLockedException.class);
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → InvalidStatusTransitionException")
    void invalidTransition() {
        EventProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "casamento", null, null, "Briefing", null);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "aprovada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-eventos";
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
