package com.meada.whatsapp.profiles.casamento.proposals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.ChecklistTaskNotFoundException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.EmptyBudgetException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.casamento.proposals.WeddingProposalService.ProposalLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o WeddingProposalService (camada 8.7) — o núcleo (order-based + funil de status, clone do
 * EventProposalService) E a escapada (checklist pré-casamento): abre proposta + snapshots; add item
 * recalcula total; updateItem recalcula (muda quantidade); deleteItem recalcula; orçar sem item →
 * empty_budget; mutar sob trava → proposal_locked; transição inválida → 409; notifica em orcada com
 * total. CRONOGRAMA ordenado por start_time. CHECKLIST: add cria done=false; toggle done=true grava
 * done_at e done=false zera; leitura ordenada por due_date asc NULLS LAST; mutar sob trava →
 * proposal_locked. FakeEvolutionSender (notifica orcada).
 */
@Import(WeddingProposalServiceTest.TestConfig.class)
class WeddingProposalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WeddingProposalService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000002");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento Svc", "casamento-svc");
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

    private WeddingProposal openProposal() {
        return service.open(COMPANY, contactId, null, null, conversationId,
            "clássico", null, 120, "Casamento grande", null);
    }

    // -------------------------------------------------------------------------
    // NÚCLEO (order-based + status)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("open tira snapshot do contact (nome/telefone) e abre em rascunho com total 0")
    void open_snapshotsContact() {
        WeddingProposal p = openProposal();
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina");
        assertThat(p.customerPhone()).isEqualTo("+5511999990180");
        assertThat(p.weddingStyle()).isEqualTo("clássico");
        assertThat(p.guestCount()).isEqualTo(120);
    }

    @Test
    @DisplayName("addItem recalcula total_cents materializado a cada item de orçamento")
    void addItem_recalcsTotal() {
        WeddingProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        service.addItem(COMPANY, p.id(), "Buffet", 1, 300000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(800000);
    }

    @Test
    @DisplayName("updateItem (muda quantidade) e deleteItem recalculam o total")
    void updateAndDelete_recalcsTotal() {
        WeddingProposal p = openProposal();
        WeddingProposalItem item = service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        WeddingProposalItem extra = service.addItem(COMPANY, p.id(), "Extra", 2, 100000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(700000);
        // update: muda a quantidade do primeiro item para 3 → 3*500000 + 200000 = 1700000.
        service.updateItem(COMPANY, p.id(), item.id(), null, 3, null);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(1700000);
        // delete do extra → 1500000.
        service.deleteItem(COMPANY, p.id(), extra.id());
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(1500000);
    }

    @Test
    @DisplayName("orçar proposta SEM item de orçamento → EmptyBudgetException")
    void orcar_emptyBudget() {
        WeddingProposal p = openProposal();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "orcada"))
            .isInstanceOf(EmptyBudgetException.class);
    }

    @Test
    @DisplayName("rascunho→orcada com total>0 → notifica o cliente com o total formatado")
    void orcar_notifiesWithTotal() {
        WeddingProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        WeddingProposal orcada = service.updateStatus(COMPANY, p.id(), "orcada");
        assertThat(orcada.status()).isEqualTo("orcada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text())
            .contains("R$ 5000,00")
            .contains("clássico");
    }

    @Test
    @DisplayName("mutar item de orçamento numa proposta travada (fechada) → ProposalLockedException")
    void addItem_lockedProposal() {
        WeddingProposal p = openProposal();
        // força o estado 'fechada' direto no banco (travado).
        jdbcTemplate.update("update wedding_proposals set status = 'fechada' where id = ?", p.id());
        assertThatThrownBy(() -> service.addItem(COMPANY, p.id(), "Extra", 1, 1000))
            .isInstanceOf(ProposalLockedException.class);
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → InvalidStatusTransitionException")
    void invalidTransition() {
        WeddingProposal p = openProposal();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "aprovada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // -------------------------------------------------------------------------
    // CRONOGRAMA (ordenado por start_time, não entra no total)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3 marcos fora de ordem → leitura ORDENADA por start_time; total intacto")
    void timeline_orderedByStartTime() {
        WeddingProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        // inserção FORA de ordem: 22:00, 17:00, 19:00.
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(22, 0), "Festa", null);
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(17, 0), "Cerimônia", null);
        service.addTimelineItem(COMPANY, p.id(), LocalTime.of(19, 0), "Jantar", null);

        List<WeddingTimelineItem> timeline = service.get(COMPANY, p.id()).orElseThrow().timeline();
        assertThat(timeline).extracting(t -> t.startTime().toString())
            .containsExactly("17:00", "19:00", "22:00");
        assertThat(timeline).extracting(WeddingTimelineItem::title)
            .containsExactly("Cerimônia", "Jantar", "Festa");
        // cronograma NÃO entra no total.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
    }

    // -------------------------------------------------------------------------
    // ESCAPADA: CHECKLIST PRÉ-CASAMENTO (estado binário + due_date)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addChecklistTask cria 'pendente' (done=false, done_at null)")
    void addChecklistTask_createsPending() {
        WeddingProposal p = openProposal();
        WeddingChecklistTask task = service.addChecklistTask(COMPANY, p.id(), "Enviar convites", null,
            LocalDate.of(2026, 10, 1));
        assertThat(task.done()).isFalse();
        assertThat(task.doneAt()).isNull();
        assertThat(task.title()).isEqualTo("Enviar convites");
        assertThat(task.dueDate().toString()).isEqualTo("2026-10-01");
    }

    @Test
    @DisplayName("toggleChecklistTask done=true grava done_at; done=false zera (round-trip)")
    void toggleChecklistTask_doneAtRoundTrip() {
        WeddingProposal p = openProposal();
        WeddingChecklistTask task = service.addChecklistTask(COMPANY, p.id(), "Provar o bolo", null, null);
        assertThat(task.done()).isFalse();
        assertThat(task.doneAt()).isNull();

        WeddingChecklistTask done = service.toggleChecklistTask(COMPANY, p.id(), task.id(), true);
        assertThat(done.done()).isTrue();
        assertThat(done.doneAt()).isNotNull();

        WeddingChecklistTask back = service.toggleChecklistTask(COMPANY, p.id(), task.id(), false);
        assertThat(back.done()).isFalse();
        assertThat(back.doneAt()).isNull();
    }

    @Test
    @DisplayName("toggleChecklistTask em tarefa inexistente → ChecklistTaskNotFoundException")
    void toggleChecklistTask_notFound() {
        WeddingProposal p = openProposal();
        assertThatThrownBy(() -> service.toggleChecklistTask(COMPANY, p.id(), UUID.randomUUID(), true))
            .isInstanceOf(ChecklistTaskNotFoundException.class);
    }

    @Test
    @DisplayName("leitura do checklist ordena por due_date asc NULLS LAST (com prazo antes do sem prazo)")
    void checklist_orderedByDueDateNullsLast() {
        WeddingProposal p = openProposal();
        // insere FORA de ordem: sem prazo, prazo tardio, prazo cedo.
        service.addChecklistTask(COMPANY, p.id(), "Sem prazo", null, null);
        service.addChecklistTask(COMPANY, p.id(), "Prazo tardio", null, LocalDate.of(2026, 12, 1));
        service.addChecklistTask(COMPANY, p.id(), "Prazo cedo", null, LocalDate.of(2026, 6, 1));

        List<WeddingChecklistTask> checklist = service.get(COMPANY, p.id()).orElseThrow().checklist();
        // due_date asc primeiro (cedo, tardio), tarefa SEM prazo (null) vai por último.
        assertThat(checklist).extracting(WeddingChecklistTask::title)
            .containsExactly("Prazo cedo", "Prazo tardio", "Sem prazo");
    }

    @Test
    @DisplayName("checklist NÃO entra no total_cents da proposta")
    void checklist_doesNotAffectTotal() {
        WeddingProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "Espaço", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        service.addChecklistTask(COMPANY, p.id(), "Enviar convites", null, null);
        service.addChecklistTask(COMPANY, p.id(), "Provar o bolo", null, null);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().checklist()).hasSize(2);
    }

    @Test
    @DisplayName("mutar tarefa de checklist numa proposta travada (fechada) → ProposalLockedException")
    void checklist_lockedByState() {
        WeddingProposal p = openProposal();
        WeddingChecklistTask task = service.addChecklistTask(COMPANY, p.id(), "Enviar convites", null, null);
        // trava: força 'fechada'.
        jdbcTemplate.update("update wedding_proposals set status = 'fechada' where id = ?", p.id());

        assertThatThrownBy(() -> service.addChecklistTask(COMPANY, p.id(), "Provar o bolo", null, null))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.toggleChecklistTask(COMPANY, p.id(), task.id(), true))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.deleteChecklistTask(COMPANY, p.id(), task.id()))
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
