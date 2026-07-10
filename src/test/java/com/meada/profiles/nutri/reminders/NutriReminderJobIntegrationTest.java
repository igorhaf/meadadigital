package com.meada.profiles.nutri.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.nutri.appointments.ConfirmacaoNutriHandler;
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
 * Integration test da onda Nutri 1 (backlog #1/#2/#5): lembrete de véspera + auto-transição +
 * régua de retomada (opt-in) via {@link NutriReminderJob}, e o loop de confirmação via
 * {@link ConfirmacaoNutriHandler}. EvolutionSender é um FAKE. Trava clínica intacta (tudo
 * logística).
 */
@Import(NutriReminderJobIntegrationTest.TestConfig.class)
class NutriReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("b0000000-0000-0000-0000-0000000000e2");
    private static final UUID INSTANCE = UUID.fromString("b0100000-0000-0000-0000-0000000000e2");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private NutriReminderJob job;
    @Autowired
    private ConfirmacaoNutriHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID professionalId;
    private UUID patientId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri Reminder", "nutri-reminder");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-ntr", "tok-ntr");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Nina')",
            contactId, COMPANY, "+5511999990271");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        professionalId = jdbcTemplate.queryForObject(
            "insert into nutri_professionals (company_id, name) values (?, 'Dra. Ana') returning id",
            UUID.class, COMPANY);
        patientId = jdbcTemplate.queryForObject(
            "insert into nutri_patients (company_id, contact_id, name) values (?, ?, 'Nina') returning id",
            UUID.class, COMPANY, contactId);
    }

    private UUID seedAppointment(String status, Instant startAt, UUID conversation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into nutri_appointments (id, company_id, professional_id, professional_name, "
                + "patient_id, patient_name, contact_id, conversation_id, appointment_type, "
                + "duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, 'Dra. Ana', ?, 'Nina', ?, ?, 'retorno', 40, ?, ?, ?)",
            id, COMPANY, professionalId, patientId, contactId, conversation,
            java.sql.Timestamp.from(startAt),
            java.sql.Timestamp.from(startAt.plus(40, ChronoUnit.MINUTES)), status);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("consulta de amanhã → lembrete 1x + idempotente; confirmação via tag com barreira")
    void reminderAndConfirmacao() {
        UUID a = seedAppointment("agendado", tomorrowAt(14), conversationId);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Dra. Ana").contains("AMANHÃ");
        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        String tag = "<confirmacao_nutri>{\"appointment_id\":\"" + a
            + "\",\"decisao\":\"confirmado\"}</confirmacao_nutri>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), tag))
            .isEmpty();   // barreira de contato.
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, tag)).isPresent();
        assertThat(statusOf(a)).isEqualTo("confirmado");
    }

    @Test
    @DisplayName("auto-transição: confirmado vencido → realizado (silencioso); agendado não")
    void autoComplete() {
        Instant past = Instant.now().minus(5, ChronoUnit.HOURS);
        UUID confirmado = seedAppointment("confirmado", past, conversationId);
        UUID agendado = seedAppointment("agendado", past, conversationId);

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmado)).isEqualTo("realizado");
        assertThat(statusOf(agendado)).isEqualTo("agendado");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("régua: OFF por default; ON → paciente inativo sem consulta futura recebe 1 toque")
    void reengagement_optIn() {
        // última realizada há 60 dias, nada futuro.
        UUID old = seedAppointment("realizado", Instant.now().minus(60, ChronoUnit.DAYS), conversationId);
        assertThat(old).isNotNull();

        assertThat(job.runReengagements()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into nutri_config (company_id, reengagement_enabled, reengagement_days) "
                + "values (?, true, 30)", COMPANY);
        assertThat(job.runReengagements()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Nina");

        // 1 toque por ciclo.
        fakeEvolution.reset();
        assertThat(job.runReengagements()).isZero();

        // consulta futura ativa suprime a régua mesmo re-armada.
        jdbcTemplate.update("update nutri_patients set reengagement_sent_at = null where id = ?", patientId);
        seedAppointment("agendado", tomorrowAt(9), conversationId);
        assertThat(job.runReengagements()).isZero();
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from nutri_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-nutri-reminder";
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
