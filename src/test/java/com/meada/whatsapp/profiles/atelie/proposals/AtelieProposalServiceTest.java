package com.meada.whatsapp.profiles.atelie.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.EmptyBudgetException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.InvalidFittingStatusException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.atelie.proposals.AtelieProposalService.ProposalLockedException;
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
 * Testa o AtelieProposalService (camada 8.14) — o núcleo (order-based + funil de status) E a escapada
 * (provas/ajustes): abre proposta + snapshots + normalização do project_type; add item recalcula
 * total; orçar sem item → empty_budget; mutar sob trava → proposal_locked; transição inválida → 409;
 * notifica em orcada com total. FITTINGS: position incremental, pendente⇄realizada com completed_at,
 * status inválido → invalid_fitting_status, reorder re-materializa position, leitura ordenada,
 * mutação sob trava → proposal_locked. FakeEvolutionSender (notifica orcada).
 */
@Import(AtelieProposalServiceTest.TestConfig.class)
class AtelieProposalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AtelieProposalService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("a7000000-0000-0000-0000-000000000002");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Svc", "atelie-svc");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990180", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private AtelieProposal openProposal() {
        return service.open(COMPANY, contactId, null, null, conversationId,
            "costura", null, null, "Vestido sob medida", null);
    }

    // -------------------------------------------------------------------------
    // NÚCLEO (order-based + status)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("open tira snapshot do contact, abre em rascunho com total 0 e project_type válido")
    void open_snapshotsContact() {
        AtelieProposal p = service.open(COMPANY, contactId, null, null, conversationId,
            "arte", "aniversário", null, "Quadro grande", null);
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina");
        assertThat(p.customerPhone()).isEqualTo("+5511999990180");
        assertThat(p.projectType()).isEqualTo("arte");
    }

    @Test
    @DisplayName("open com project_type ausente/inválido → normaliza para 'costura'")
    void open_defaultsProjectType() {
        AtelieProposal absent = service.open(COMPANY, contactId, null, null, conversationId,
            null, null, null, "Briefing", null);
        assertThat(absent.projectType()).isEqualTo("costura");
        AtelieProposal invalid = service.open(COMPANY, contactId, null, null, conversationId,
            "xpto", null, null, "Briefing", null);
        assertThat(invalid.projectType()).isEqualTo("costura");
    }

    @Test
    @DisplayName("addItem recalcula total_cents materializado a cada item de orçamento")
    void addItem_recalcsTotal() {
        AtelieProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Tecido", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        service.addItem(COMPANY, p.id(), "Mão de obra", 1, 300000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(800000);
    }

    @Test
    @DisplayName("updateItem e deleteItem recalculam o total")
    void updateAndDelete_recalcsTotal() {
        AtelieProposal p = openProposal();
        AtelieProposalItem item = service.addItem(COMPANY, p.id(), "Tecido", 1, 500000);
        AtelieProposalItem extra = service.addItem(COMPANY, p.id(), "Extra", 2, 100000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(700000);
        // update: muda quantidade do primeiro item para 3 → 1500000 + 200000 = 1700000.
        service.updateItem(COMPANY, p.id(), item.id(), null, 3, null);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(1700000);
        // delete do extra → 1500000.
        service.deleteItem(COMPANY, p.id(), extra.id());
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(1500000);
    }

    @Test
    @DisplayName("orçar proposta SEM item de orçamento → EmptyBudgetException")
    void orcar_emptyBudget() {
        AtelieProposal p = openProposal();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "orcada"))
            .isInstanceOf(EmptyBudgetException.class);
    }

    @Test
    @DisplayName("rascunho→orcada com total>0 → notifica o cliente com o total formatado")
    void orcar_notifiesWithTotal() {
        AtelieProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Tecido", 1, 500000);
        AtelieProposal orcada = service.updateStatus(COMPANY, p.id(), "orcada");
        assertThat(orcada.status()).isEqualTo("orcada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text())
            .contains("R$ 5000,00");
    }

    @Test
    @DisplayName("mutar item de orçamento numa proposta travada (fechada) → ProposalLockedException")
    void addItem_lockedProposal() {
        AtelieProposal p = openProposal();
        jdbcTemplate.update("update atelie_proposals set status = 'fechada' where id = ?", p.id());
        assertThatThrownBy(() -> service.addItem(COMPANY, p.id(), "Extra", 1, 1000))
            .isInstanceOf(ProposalLockedException.class);
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → InvalidStatusTransitionException")
    void invalidTransition() {
        AtelieProposal p = openProposal();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "aprovada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // -------------------------------------------------------------------------
    // ESCAPADA: PROVAS/AJUSTES (fittings)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addFitting cria 'pendente' com position incremental (0,1,2)")
    void addFitting_incrementalPosition() {
        AtelieProposal p = openProposal();
        AtelieFitting f0 = service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        AtelieFitting f1 = service.addFitting(COMPANY, p.id(), "2ª prova", null, null);
        AtelieFitting f2 = service.addFitting(COMPANY, p.id(), "Ajuste final", null, null);
        assertThat(f0.status()).isEqualTo("pendente");
        assertThat(f0.position()).isZero();
        assertThat(f1.position()).isEqualTo(1);
        assertThat(f2.position()).isEqualTo(2);
        assertThat(f0.completedAt()).isNull();
    }

    @Test
    @DisplayName("leitura das provas vem ORDENADA por position asc")
    void fittings_readOrderedByPosition() {
        AtelieProposal p = openProposal();
        service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        service.addFitting(COMPANY, p.id(), "2ª prova", null, null);
        service.addFitting(COMPANY, p.id(), "Ajuste final", null, null);
        List<AtelieFitting> fittings = service.get(COMPANY, p.id()).orElseThrow().fittings();
        assertThat(fittings).extracting(AtelieFitting::title)
            .containsExactly("1ª prova", "2ª prova", "Ajuste final");
        assertThat(fittings).extracting(AtelieFitting::position)
            .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("transitionFitting pendente→realizada grava completed_at; realizada→pendente zera (null)")
    void transitionFitting_completedAtRoundTrip() {
        AtelieProposal p = openProposal();
        AtelieFitting f = service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        assertThat(f.completedAt()).isNull();

        AtelieFitting done = service.transitionFitting(COMPANY, p.id(), f.id(), "realizada");
        assertThat(done.status()).isEqualTo("realizada");
        assertThat(done.completedAt()).isNotNull();

        AtelieFitting back = service.transitionFitting(COMPANY, p.id(), f.id(), "pendente");
        assertThat(back.status()).isEqualTo("pendente");
        assertThat(back.completedAt()).isNull();
    }

    @Test
    @DisplayName("transitionFitting com status inválido → InvalidFittingStatusException")
    void transitionFitting_invalidStatus() {
        AtelieProposal p = openProposal();
        AtelieFitting f = service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        assertThatThrownBy(() -> service.transitionFitting(COMPANY, p.id(), f.id(), "xpto"))
            .isInstanceOf(InvalidFittingStatusException.class);
    }

    @Test
    @DisplayName("reorderFittings re-materializa position 0..N a partir da lista ordenada")
    void reorderFittings_rematerializesPosition() {
        AtelieProposal p = openProposal();
        AtelieFitting f0 = service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        AtelieFitting f1 = service.addFitting(COMPANY, p.id(), "2ª prova", null, null);
        AtelieFitting f2 = service.addFitting(COMPANY, p.id(), "Ajuste final", null, null);
        // inverte a ordem: f2, f0, f1.
        List<AtelieFitting> reordered = service.reorderFittings(COMPANY, p.id(), List.of(f2.id(), f0.id(), f1.id()));
        assertThat(reordered).extracting(AtelieFitting::title)
            .containsExactly("Ajuste final", "1ª prova", "2ª prova");
        assertThat(reordered).extracting(AtelieFitting::position)
            .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("provas/ajustes NÃO entram no total_cents da proposta")
    void fittings_doNotAffectTotal() {
        AtelieProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Tecido", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        service.addFitting(COMPANY, p.id(), "Ajuste final", null, null);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().fittings()).hasSize(2);
    }

    @Test
    @DisplayName("mutar prova/ajuste numa proposta travada (fechada) → ProposalLockedException")
    void fitting_lockedByState() {
        AtelieProposal p = openProposal();
        AtelieFitting f = service.addFitting(COMPANY, p.id(), "1ª prova", null, null);
        jdbcTemplate.update("update atelie_proposals set status = 'fechada' where id = ?", p.id());

        assertThatThrownBy(() -> service.addFitting(COMPANY, p.id(), "2ª prova", null, null))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.transitionFitting(COMPANY, p.id(), f.id(), "realizada"))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.deleteFitting(COMPANY, p.id(), f.id()))
            .isInstanceOf(ProposalLockedException.class);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-atelie";
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
