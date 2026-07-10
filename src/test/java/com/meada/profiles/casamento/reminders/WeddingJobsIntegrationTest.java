package com.meada.profiles.casamento.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
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

/**
 * Integration test dos jobs do casamento (onda 1, backlog #2/#4/#16) contra PostgreSQL real:
 * lembrete D-3 de checklist e de parcela (idempotente por linha+data; toggles respeitados),
 * auto-realizada (fechada com wedding_date passado) e aniversário de casamento (1x/ano).
 * A lógica roda via os métodos públicos (sem scheduler). EvolutionSender é FAKE.
 */
@Import(WeddingJobsIntegrationTest.TestConfig.class)
class WeddingJobsIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000085");
    private static final UUID INSTANCE = UUID.fromString("cf100000-0000-0000-0000-000000000085");

    @Autowired
    private WeddingReminderJob reminderJob;
    @Autowired
    private WeddingAutoTransitionJob autoTransitionJob;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento Jobs", "casamento-jobs");
        UUID contact = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cas", "tok-cas");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Ana')",
            contact, COMPANY, "+5511999990185");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contact, INSTANCE);
    }

    private UUID seedProposal(String status, LocalDate weddingDate, UUID conversation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into wedding_proposals (id, company_id, conversation_id, customer_name, status, wedding_date, "
                + "total_cents, closed_at) values (?, ?, ?, 'Ana & João', ?, ?, 500000, "
                + "case when ? in ('realizada','recusada','cancelada') then now() end)",
            id, COMPANY, conversation, status,
            weddingDate == null ? null : java.sql.Date.valueOf(weddingDate), status);
        return id;
    }

    @Test
    @DisplayName("checklist D-3 + parcela D-3 → lembrete enviado + idempotente na 2ª passada")
    void reminders_checklistAndPayment() {
        UUID proposal = seedProposal("fechada", LocalDate.now().plusMonths(3), conversationId);
        jdbcTemplate.update(
            "insert into wedding_checklist_tasks (company_id, proposal_id, title, due_date) values (?, ?, 'Prova do vestido', ?)",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(2)));
        jdbcTemplate.update(
            "insert into wedding_payments (company_id, proposal_id, kind, due_date, amount_cents) values (?, ?, 'parcela', ?, 150000)",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(3)));

        assertThat(reminderJob.runReminders()).isEqualTo(2);
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Prova do vestido");
        assertThat(fakeEvolution.sent().get(1).text()).contains("vence");

        fakeEvolution.reset();
        assertThat(reminderJob.runReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("toggles desligados na config → nenhum lembrete")
    void reminders_disabled() {
        jdbcTemplate.update(
            "insert into wedding_config (company_id, checklist_reminder_enabled, payment_reminder_enabled) "
                + "values (?, false, false)", COMPANY);
        UUID proposal = seedProposal("fechada", LocalDate.now().plusMonths(3), conversationId);
        jdbcTemplate.update(
            "insert into wedding_checklist_tasks (company_id, proposal_id, title, due_date) values (?, ?, 'Buffet', ?)",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        jdbcTemplate.update(
            "insert into wedding_payments (company_id, proposal_id, kind, due_date, amount_cents) values (?, ?, 'sinal', ?, 100000)",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(1)));

        assertThat(reminderJob.runReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("tarefa concluída, parcela paga e prazo distante → nada a lembrar")
    void reminders_notDue() {
        UUID proposal = seedProposal("fechada", LocalDate.now().plusMonths(3), conversationId);
        jdbcTemplate.update(
            "insert into wedding_checklist_tasks (company_id, proposal_id, title, due_date, done, done_at) "
                + "values (?, ?, 'Já feita', ?, true, now())",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
        jdbcTemplate.update(
            "insert into wedding_checklist_tasks (company_id, proposal_id, title, due_date) values (?, ?, 'Longe', ?)",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(30)));
        jdbcTemplate.update(
            "insert into wedding_payments (company_id, proposal_id, kind, due_date, amount_cents, paid, paid_at) "
                + "values (?, ?, 'parcela', ?, 100000, true, now())",
            COMPANY, proposal, java.sql.Date.valueOf(LocalDate.now().plusDays(1)));

        assertThat(reminderJob.runReminders()).isZero();
    }

    @Test
    @DisplayName("auto-realizada: fechada com wedding_date passado vira realizada + dispara pós-casamento")
    void autoTransition_completes() {
        UUID past = seedProposal("fechada", LocalDate.now().minusDays(2), conversationId);
        UUID future = seedProposal("fechada", LocalDate.now().plusDays(10), conversationId);

        assertThat(autoTransitionJob.runAutoTransitions()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "select status from wedding_proposals where id = ?", String.class, past)).isEqualTo("realizada");
        assertThat(jdbcTemplate.queryForObject(
            "select status from wedding_proposals where id = ?", String.class, future)).isEqualTo("fechada");
        // onda 2 (backlog #6): realizada dispara o pós-casamento (toggle default ON).
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("honra");
    }

    @Test
    @DisplayName("aniversário: realizada há 1 ano (dia/mês de hoje) → parabéns 1x/ano")
    void anniversary_congratulatesOncePerYear() {
        UUID anniversary = seedProposal("realizada", LocalDate.now().minusYears(1), conversationId);
        seedProposal("realizada", LocalDate.now().minusYears(1).minusDays(30), conversationId);   // outro dia

        assertThat(autoTransitionJob.runAutoTransitions()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("aniversário de casamento");
        assertThat(jdbcTemplate.queryForObject(
            "select anniversary_notified_year from wedding_proposals where id = ?", Integer.class, anniversary))
            .isEqualTo(LocalDate.now().getYear());

        // 2ª passada no mesmo ano → não repete.
        fakeEvolution.reset();
        assertThat(autoTransitionJob.runAutoTransitions()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-casamento-jobs";
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
