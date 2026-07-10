package com.meada.profiles.viagens.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.viagens.proposals.TravelProposalService;
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
 * Integration test da onda Viagens (backlog #1/#2/#8) contra PostgreSQL real: lembretes de viagem
 * (D-7/D0/D+2) + follow-up de orçada parada via {@link TravelReminderJob} (métodos públicos, sem o
 * scheduler) e o GATE do sinal no fechamento via {@link TravelProposalService}. EvolutionSender é
 * um FAKE que só registra os envios.
 */
@Import(TravelReminderJobIntegrationTest.TestConfig.class)
class TravelReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("a8000000-0000-0000-0000-0000000000c1");
    private static final UUID INSTANCE = UUID.fromString("a8100000-0000-0000-0000-0000000000c1");

    @Autowired
    private TravelReminderJob job;
    @Autowired
    private TravelProposalService proposalService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID conversationId;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'viagens')",
            COMPANY, "Viagens Onda", "viagens-onda");
        UUID contact = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-vgs", "tok-vgs");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511999990187", "Helena");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contact, INSTANCE);
    }

    private UUID seedProposal(String status, UUID conversation, LocalDate start, LocalDate end) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into travel_proposals (id, company_id, conversation_id, customer_name, destination, "
                + "start_date, end_date, status) values (?, ?, ?, 'Helena', 'Lisboa', ?, ?, ?)",
            id, COMPANY, conversation,
            start == null ? null : java.sql.Date.valueOf(start),
            end == null ? null : java.sql.Date.valueOf(end), status);
        return id;
    }

    // -------------------------------------------------------------------------
    // #2 — lembretes de viagem
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("fechada com ida em D+7 → checklist enviado + marcado + idempotente")
    void pretrip_remindedOnceThenIdempotent() {
        UUID p = seedProposal("fechada", conversationId, today.plusDays(7), today.plusDays(14));

        assertThat(job.runTripReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("7 dias").contains("Lisboa");

        fakeEvolution.reset();
        assertThat(job.runTripReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();

        // remarcada pra outra ida → rearma o D-7.
        jdbcTemplate.update("update travel_proposals set start_date = ? where id = ?",
            java.sql.Date.valueOf(today.plusDays(7)), p); // mesma data → não rearma
        assertThat(job.runTripReminders()).isZero();
    }

    @Test
    @DisplayName("fechada com ida HOJE → boa viagem; volta em D-2 → pós-viagem/NPS (fechada OU realizada)")
    void startAndPosttrip() {
        seedProposal("fechada", conversationId, today, today.plusDays(5));
        seedProposal("realizada", conversationId, today.minusDays(9), today.minusDays(2));

        assertThat(job.runTripReminders()).isEqualTo(2);
        List<String> texts = fakeEvolution.sent().stream().map(SentMessage::text).toList();
        assertThat(texts).anySatisfy(t -> assertThat(t).contains("grande dia"));
        assertThat(texts).anySatisfy(t -> assertThat(t).contains("de volta"));
    }

    @Test
    @DisplayName("toggle desligado na config → nada (opt-out por tenant)")
    void reminderDisabled_nothing() {
        jdbcTemplate.update(
            "insert into travel_config (company_id, trip_reminder_enabled, quote_followup_enabled) "
                + "values (?, false, false)", COMPANY);
        seedProposal("fechada", conversationId, today.plusDays(7), null);
        assertThat(job.runTripReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("status não-fechada ou sem data → nada")
    void notDue_nothing() {
        seedProposal("aprovada", conversationId, today.plusDays(7), null);  // status não-fechada
        seedProposal("fechada", conversationId, null, null);                // sem datas
        assertThat(job.runTripReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("proposta manual sem conversa → marca sem envio (não revarre eternamente)")
    void noChannel_markedWithoutSend() {
        UUID p = seedProposal("fechada", null, today.plusDays(7), null);

        assertThat(job.runTripReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).isEmpty();
        java.sql.Date marked = jdbcTemplate.queryForObject(
            "select pretrip_reminded_start_date from travel_proposals where id = ?",
            java.sql.Date.class, p);
        assertThat(marked.toLocalDate()).isEqualTo(today.plusDays(7));
    }

    // -------------------------------------------------------------------------
    // #8 — follow-up de orçada parada
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("orcada parada há mais que a janela → cutucada 1x; re-orçar rearma")
    void quoteFollowup_onceThenRearmedByStatusChange() {
        UUID p = seedProposal("orcada", conversationId, null, null);
        jdbcTemplate.update("update travel_proposals set total_cents = 500000, "
            + "status_updated_at = now() - interval '3 days' where id = ?", p);

        assertThat(job.runQuoteFollowups()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Lisboa").contains("R$ 5000,00");

        // idempotente dentro do mesmo episódio.
        fakeEvolution.reset();
        assertThat(job.runQuoteFollowups()).isZero();

        // re-orçada (status_updated_at avança) e parada de novo → rearma.
        jdbcTemplate.update("update travel_proposals set status_updated_at = now() - interval '3 days', "
            + "quote_followup_sent_at = now() - interval '5 days' where id = ?", p);
        assertThat(job.runQuoteFollowups()).isEqualTo(1);
    }

    @Test
    @DisplayName("orcada recente (dentro da janela) → nada")
    void quoteRecent_nothing() {
        seedProposal("orcada", conversationId, null, null);
        assertThat(job.runQuoteFollowups()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // #1 — sinal com gate no fechamento
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sinal registrado e não pago → aprovada→fechada 409 deposit_required; pago → fecha")
    void depositGate() {
        UUID p = seedProposal("aprovada", conversationId, null, null);
        proposalService.setDeposit(COMPANY, p, 100000, false);

        assertThatThrownBy(() -> proposalService.updateStatus(COMPANY, p, "fechada"))
            .isInstanceOf(TravelProposalService.DepositRequiredException.class);

        proposalService.setDeposit(COMPANY, p, 100000, true);
        assertThat(proposalService.updateStatus(COMPANY, p, "fechada").status()).isEqualTo("fechada");
        assertThat(proposalService.get(COMPANY, p).orElseThrow().depositPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("marcar pago sem valor → invalid_deposit; sem sinal registrado o fechamento é livre")
    void depositValidationAndFreeClose() {
        UUID p = seedProposal("aprovada", conversationId, null, null);

        assertThatThrownBy(() -> proposalService.setDeposit(COMPANY, p, null, true))
            .isInstanceOf(TravelProposalService.InvalidDepositException.class);

        assertThat(proposalService.updateStatus(COMPANY, p, "fechada").status()).isEqualTo("fechada");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-viagens-reminder";
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
