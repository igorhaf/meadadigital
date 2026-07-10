package com.meada.profiles.escola.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.escola.waitlist.EscolaWaitlistService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Escola 1 (backlog #1/#2/#4/#10): lista de espera por turma (posição
 * derivada + aviso humano de vaga), lembretes de visita D-1/D0 com rearm, auto-transição de
 * visita passada e régua de mensalidade em aberto (opt-in OFF). EvolutionSender é um FAKE.
 */
@Import(EscolaOnda1IntegrationTest.TestConfig.class)
class EscolaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("d4000000-0000-0000-0000-000000000109");
    private static final UUID INSTANCE = UUID.fromString("d4100000-0000-0000-0000-000000000109");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private EscolaReminderJob job;
    @Autowired
    private EscolaWaitlistService waitlistService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID classId;
    private UUID studentId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola Onda", "escola-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-esc", "tok-esc");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Paula')",
            contactId, COMPANY, "+5511999990351");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        classId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Pré I', 'Infantil', 'manha', 1, 60000)", classId, COMPANY);
        studentId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Theo')",
            studentId, COMPANY, contactId);
    }

    @Test
    @DisplayName("waitlist: enfileira com posição derivada; duplicata é no-op; avisar vaga é ação humana")
    void waitlist() {
        assertThat(waitlistService.enqueue(COMPANY, classId, contactId, studentId)).isTrue();
        assertThat(waitlistService.enqueue(COMPANY, classId, contactId, studentId)).isFalse();   // dup.

        UUID contact2 = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Rui')",
            contact2, COMPANY, "+5511999990352");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", UUID.randomUUID(), COMPANY, contact2, INSTANCE);
        assertThat(waitlistService.enqueue(COMPANY, classId, contact2, null)).isTrue();   // snapshot do contato.

        List<Map<String, Object>> fila = waitlistService.listByClass(COMPANY, classId);
        assertThat(fila).hasSize(2);
        assertThat(fila.get(0).get("position")).isEqualTo(1L);
        assertThat(fila.get(0).get("studentName")).isEqualTo("Theo");
        assertThat(fila.get(1).get("position")).isEqualTo(2L);
        assertThat(fila.get(1).get("studentName")).isEqualTo("Rui");
        assertThat(waitlistService.countPending(COMPANY, classId)).isEqualTo(2);

        // avisar o 1º da fila (ação humana) → mensagem + status avisada.
        UUID first = (UUID) fila.get(0).get("id");
        assertThat(waitlistService.notifyOpening(COMPANY, first)).isPresent();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Abriu uma vaga").contains("Pré I").contains("Theo");
        assertThat(waitlistService.countPending(COMPANY, classId)).isEqualTo(1);

        // avisada não é re-avisável; gestão fecha o ciclo.
        assertThat(waitlistService.notifyOpening(COMPANY, first)).isEmpty();
        assertThat(waitlistService.updateStatus(COMPANY, first, "convertida")).isTrue();
    }

    @Test
    @DisplayName("visita: lembrete D-1 e no DIA 1x cada (rearm ao remarcar); passada vira realizada")
    void visitRemindersAndAutoComplete() {
        LocalDate today = LocalDate.now(SP);
        UUID amanha = seedVisit(today.plusDays(1));
        UUID hoje = seedVisit(today);
        UUID passada = seedVisit(today.minusDays(1));

        assertThat(job.runVisitReminders()).isEqualTo(2);   // D-1 (amanha) + D0 (hoje).
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("AMANHÃ"))
            .anyMatch(t -> t.contains("HOJE"));

        fakeEvolution.reset();
        assertThat(job.runVisitReminders()).isZero();   // idempotente por data.

        // remarcar a de amanhã REARMA a janela D-1.
        jdbcTemplate.update("update escola_visits set visit_date = ?, reminded1_visit_date = ? where id = ?",
            Date.valueOf(today.plusDays(1)), Date.valueOf(today.minusDays(3)), amanha);
        assertThat(job.runVisitReminders()).isEqualTo(1);

        // auto-transição: só a passada vira realizada.
        assertThat(job.runVisitAutoComplete()).isEqualTo(1);
        assertThat(visitStatus(passada)).isEqualTo("realizada");
        assertThat(visitStatus(hoje)).isEqualTo("agendada");
    }

    @Test
    @DisplayName("mensalidade em aberto: OFF por default; ON → 1 lembrete por mês; paga não lembra")
    void paymentReminders_optIn() {
        UUID enrollment = jdbcTemplate.queryForObject(
            "insert into escola_enrollments (company_id, class_id, student_id, conversation_id, contact_id, "
                + "student_name, class_name, class_grade, class_shift, class_monthly_cents, status, start_date) "
                + "values (?, ?, ?, ?, ?, 'Theo', 'Pré I', 'Infantil', 'manha', 60000, 'ativa', "
                + "current_date - interval '60 days') returning id",
            UUID.class, COMPANY, classId, studentId, conversationId, contactId);

        assertThat(job.runPaymentReminders()).isZero();   // default OFF.

        // ON com vencimento dia 1 (qualquer dia do mês já passou do vencimento).
        jdbcTemplate.update(
            "insert into escola_config (company_id, payment_reminder_enabled, payment_due_day) "
                + "values (?, true, 1)", COMPANY);
        assertThat(job.runPaymentReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Theo").contains("em aberto").contains("600,00");

        // 1 por mês.
        fakeEvolution.reset();
        assertThat(job.runPaymentReminders()).isZero();

        // mês pago não lembra (rearmado pro próximo mês, aqui simulado limpando o marker).
        jdbcTemplate.update("update escola_enrollments set payment_reminded_month = null where id = ?", enrollment);
        jdbcTemplate.update(
            "insert into escola_payments (company_id, enrollment_id, reference_month, amount_cents) "
                + "values (?, ?, date_trunc('month', now())::date, 60000)", COMPANY, enrollment);
        assertThat(job.runPaymentReminders()).isZero();
    }

    private UUID seedVisit(LocalDate date) {
        return jdbcTemplate.queryForObject(
            "insert into escola_visits (company_id, contact_id, conversation_id, visitor_name, visit_date, period) "
                + "values (?, ?, ?, 'Paula', ?, 'manha') returning id",
            UUID.class, COMPANY, contactId, conversationId, Date.valueOf(date));
    }

    private String visitStatus(UUID id) {
        return jdbcTemplate.queryForObject("select status from escola_visits where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-escola-onda";
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
