package com.meada.profiles.otica.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.otica.appointments.ConfirmacaoExameHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Ótica 1 (backlog #1/#2): lembrete de exame na véspera + follow-up de
 * óculos PRONTO parado via {@link OticaReminderJob}, e o loop de confirmação via
 * {@link ConfirmacaoExameHandler} (tag {@code <confirmacao_exame>}, barreira de contato).
 * EvolutionSender é um FAKE.
 */
@Import(OticaReminderJobIntegrationTest.TestConfig.class)
class OticaReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("ae000000-0000-0000-0000-0000000000c2");
    private static final UUID INSTANCE = UUID.fromString("ae100000-0000-0000-0000-0000000000c2");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private OticaReminderJob job;
    @Autowired
    private ConfirmacaoExameHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID professionalId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Otica Reminder", "otica-reminder");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-otc", "tok-otc");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Otavio')",
            contactId, COMPANY, "+5511999990251");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        professionalId = jdbcTemplate.queryForObject(
            "insert into otica_professionals (company_id, name) values (?, 'Dra. Vera') returning id",
            UUID.class, COMPANY);
    }

    private UUID seedExam(String status, Instant startAt, UUID conversation, UUID contact) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into otica_exam_appointments (id, company_id, professional_id, conversation_id, "
                + "contact_id, customer_name, professional_name, start_at, duration_minutes, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Otavio', 'Dra. Vera', ?, 30, ?, ?)",
            id, COMPANY, professionalId, conversation, contact,
            java.sql.Timestamp.from(startAt),
            java.sql.Timestamp.from(startAt.plus(30, ChronoUnit.MINUTES)), status);
        return id;
    }

    private UUID seedProntoOrder(int daysAgo) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into otica_orders (id, company_id, conversation_id, contact_id, status, "
                + "subtotal_cents, total_cents) values (?, ?, ?, ?, 'pronto', 50000, 50000)",
            id, COMPANY, conversationId, contactId);
        jdbcTemplate.update(
            "update otica_orders set status_updated_at = now() - make_interval(days => ?) where id = ?",
            daysAgo, id);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("exame de amanhã → lembrete 1x + idempotente; toggle off → nada")
    void examReminder_onceAndToggle() {
        seedExam("agendado", tomorrowAt(14), conversationId, contactId);

        assertThat(job.runExamReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Dra. Vera").contains("AMANHÃ");

        fakeEvolution.reset();
        assertThat(job.runExamReminders()).isZero();

        jdbcTemplate.update(
            "insert into otica_config (company_id, exam_reminder_enabled) values (?, false)", COMPANY);
        seedExam("confirmado", tomorrowAt(16), conversationId, contactId);
        assertThat(job.runExamReminders()).isZero();
    }

    @Test
    @DisplayName("óculos pronto há 3+ dias → follow-up 1x por episódio; recente → nada")
    void pickupFollowup() {
        seedProntoOrder(5);
        seedProntoOrder(1);   // dentro da janela (default 3) → não cutuca.

        assertThat(job.runPickupFollowups()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("PRONTO");

        fakeEvolution.reset();
        assertThat(job.runPickupFollowups()).isZero();   // idempotente no episódio.
    }

    @Test
    @DisplayName("<confirmacao_exame>: SIM confirma com barreira de contato; desmarcar cancela")
    void confirmacaoTag() {
        UUID e = seedExam("agendado", tomorrowAt(14), conversationId, contactId);
        String tag = "<confirmacao_exame>{\"exam_id\":\"" + e
            + "\",\"decisao\":\"confirmado\"}</confirmacao_exame>";

        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), tag))
            .isEmpty();
        assertThat(statusOf(e)).isEqualTo("agendado");

        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, tag)).isPresent();
        assertThat(statusOf(e)).isEqualTo("confirmado");

        String cancel = "<confirmacao_exame>{\"exam_id\":\"" + e
            + "\",\"decisao\":\"cancelado\"}</confirmacao_exame>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, cancel)).isPresent();
        assertThat(statusOf(e)).isEqualTo("cancelado");
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from otica_exam_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-otica-reminder";
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
