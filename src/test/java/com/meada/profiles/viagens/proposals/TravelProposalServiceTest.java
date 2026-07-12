package com.meada.profiles.viagens.proposals;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.viagens.proposals.TravelProposalService.EmptyBudgetException;
import com.meada.profiles.viagens.proposals.TravelProposalService.InvalidStatusTransitionException;
import com.meada.profiles.viagens.proposals.TravelProposalService.ProposalLockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o TravelProposalService (camada 8.18 / perfil viagens): abre proposta + snapshots; add item de
 * cotação recalcula total; orçar sem item → empty_budget; mutar item em proposta travada →
 * proposal_locked; transição inválida → 409; notifica em orcada com total. ITINERÁRIO (a escapada
 * multi-dia): add com day_number incremental; reorder re-materializa day_number 1..N; leitura ORDENADA
 * por day_date NULLS LAST + day_number (dia SEM day_date ordena por ÚLTIMO); trava junto com a cotação
 * (proposal_locked). FakeEvolutionSender (notifica orcada).
 */
@Import(TravelProposalServiceTest.TestConfig.class)
class TravelProposalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TravelProposalService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ce100000-0000-0000-0000-000000000003");
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'viagens')",
            COMPANY, "Viagens Svc", "viagens-svc");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999991280", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private TravelProposal openProposal() {
        return service.open(COMPANY, contactId, null, null, conversationId,
            "Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 8), 2, "lua-de-mel", "Briefing", null);
    }

    @Test
    @DisplayName("open tira snapshot do contact e abre em rascunho com total 0")
    void open_snapshotsContact() {
        TravelProposal p = openProposal();
        assertThat(p.status()).isEqualTo("rascunho");
        assertThat(p.totalCents()).isZero();
        assertThat(p.customerName()).isEqualTo("Marina");
        assertThat(p.customerPhone()).isEqualTo("+5511999991280");
        assertThat(p.destination()).isEqualTo("Paris");
        assertThat(p.numTravelers()).isEqualTo(2);
        assertThat(p.travelStyle()).isEqualTo("lua-de-mel");
    }

    @Test
    @DisplayName("addItem recalcula total_cents materializado a cada item de cotação")
    void addItem_recalcsTotal() {
        TravelProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "aereo", "Passagens", 2, 250000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        service.addItem(COMPANY, p.id(), "hospedagem", "Hotel 7 noites", 1, 300000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(800000);
    }

    @Test
    @DisplayName("updateItem parcial (só quantity / só preço) materializa line_total com os valores FINAIS")
    void updateItem_partial_materializesFinalLineTotal() {
        TravelProposal p = openProposal();
        TravelProposalItem item = service.addItem(COMPANY, p.id(), "aereo", "Passagens", 2, 10000);
        assertThat(item.lineTotalCents()).isEqualTo(20000);

        // Muda SÓ a quantidade → 3 × 10000 (unit mantido) = 30000. Regressão real: a SET clause
        // referenciando quantity * unit_price_cents lia os valores ANTIGOS da linha e gravava 20000.
        TravelProposalItem updated = service.updateItem(COMPANY, p.id(), item.id(), null, null, 3, null);
        assertThat(updated.lineTotalCents()).isEqualTo(30000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(30000);

        // Muda SÓ o preço unitário → 3 × 20000 = 60000.
        TravelProposalItem repriced = service.updateItem(COMPANY, p.id(), item.id(), null, null, null, 20000);
        assertThat(repriced.lineTotalCents()).isEqualTo(60000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(60000);
    }

    @Test
    @DisplayName("orçar proposta SEM item de cotação → EmptyBudgetException")
    void orcar_emptyBudget() {
        TravelProposal p = openProposal();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "orcada"))
            .isInstanceOf(EmptyBudgetException.class);
    }

    @Test
    @DisplayName("rascunho→orcada com total>0 → notifica o cliente com o total formatado + destino")
    void orcar_notifiesWithTotal() {
        TravelProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "aereo", "Passagens", 1, 500000);
        TravelProposal orcada = service.updateStatus(COMPANY, p.id(), "orcada");
        assertThat(orcada.status()).isEqualTo("orcada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text())
            .contains("R$ 5000,00")
            .contains("Paris");
    }

    @Test
    @DisplayName("mutar item de cotação numa proposta travada (fechada) → ProposalLockedException")
    void addItem_lockedProposal() {
        TravelProposal p = openProposal();
        jdbcTemplate.update("update travel_proposals set status = 'fechada' where id = ?", p.id());
        assertThatThrownBy(() -> service.addItem(COMPANY, p.id(), "outro", "Extra", 1, 1000))
            .isInstanceOf(ProposalLockedException.class);
    }

    @Test
    @DisplayName("transição inválida (rascunho→aprovada) → InvalidStatusTransitionException")
    void invalidTransition() {
        TravelProposal p = openProposal();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, p.id(), "aprovada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ---- ITINERÁRIO (a escapada multi-dia) ----

    @Test
    @DisplayName("addItineraryDay cria com day_number incremental (1, 2, 3) e NÃO altera o total")
    void itinerary_incrementalDayNumber() {
        TravelProposal p = openProposal();
        service.addItem(COMPANY, p.id(), "aereo", "Passagens", 1, 500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);

        TravelItineraryDay d1 = service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 1), "Chegada", null);
        TravelItineraryDay d2 = service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 2), "City tour", null);
        TravelItineraryDay d3 = service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 3), "Versalhes", null);
        assertThat(d1.dayNumber()).isEqualTo(1);
        assertThat(d2.dayNumber()).isEqualTo(2);
        assertThat(d3.dayNumber()).isEqualTo(3);

        // itinerário não mexe no total.
        assertThat(service.get(COMPANY, p.id()).orElseThrow().totalCents()).isEqualTo(500000);
        assertThat(service.get(COMPANY, p.id()).orElseThrow().itinerary()).hasSize(3);
    }

    @Test
    @DisplayName("leitura ORDENADA por day_date NULLS LAST + day_number (dia sem data ordena por ÚLTIMO)")
    void itinerary_orderedByDateNullsLast() {
        TravelProposal p = openProposal();
        // inserção FORA de ordem; um dia SEM day_date (deve ir pro fim).
        service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 3), "Dia 3", null); // day_number 1
        service.addItineraryDay(COMPANY, p.id(), null, "Dia em aberto", null);              // day_number 2, sem data
        service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 1), "Dia 1", null); // day_number 3

        List<TravelItineraryDay> itinerary = service.get(COMPANY, p.id()).orElseThrow().itinerary();
        assertThat(itinerary).extracting(TravelItineraryDay::title)
            .containsExactly("Dia 1", "Dia 3", "Dia em aberto");
    }

    @Test
    @DisplayName("reorder re-materializa day_number sequencial 1..N na ordem recebida")
    void itinerary_reorderRematerializesDayNumber() {
        TravelProposal p = openProposal();
        TravelItineraryDay a = service.addItineraryDay(COMPANY, p.id(), null, "A", null); // day 1
        TravelItineraryDay b = service.addItineraryDay(COMPANY, p.id(), null, "B", null); // day 2
        TravelItineraryDay c = service.addItineraryDay(COMPANY, p.id(), null, "C", null); // day 3

        // nova ordem: C, A, B → day_number 1, 2, 3 respectivamente.
        List<TravelItineraryDay> reordered = service.reorderItinerary(COMPANY, p.id(), List.of(c.id(), a.id(), b.id()));
        // a leitura volta ordenada por day_date NULLS LAST + day_number; todos sem data → por day_number.
        assertThat(reordered).extracting(TravelItineraryDay::title).containsExactly("C", "A", "B");
        assertThat(reordered).extracting(TravelItineraryDay::dayNumber).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("add/update/delete/reorder de dia sob trava de estado (fechada) → proposal_locked")
    void itinerary_lockedByState() {
        TravelProposal p = openProposal();
        UUID dayId = service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 1), "Chegada", null).id();
        jdbcTemplate.update("update travel_proposals set status = 'fechada' where id = ?", p.id());

        assertThatThrownBy(() -> service.addItineraryDay(COMPANY, p.id(), LocalDate.of(2026, 12, 2), "City tour", null))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.updateItineraryDay(COMPANY, p.id(), dayId, null, false, null, false, "Novo", null, false))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.deleteItineraryDay(COMPANY, p.id(), dayId))
            .isInstanceOf(ProposalLockedException.class);
        assertThatThrownBy(() -> service.reorderItinerary(COMPANY, p.id(), List.of(dayId)))
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
