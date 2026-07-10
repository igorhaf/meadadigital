package com.meada.profiles.dental.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.dental.appointments.ConfirmacaoConsultaHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.Timestamp;
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
 * Integration test da onda Dental 1 (backlog #1/#3/#5): lembrete D-1 com confirmação (SÓ
 * confirma — cancelamento pela IA segue bloqueado), auto-realizada e recall de manutenção
 * (opt-in OFF, por episódio). EvolutionSender é um FAKE.
 */
@Import(DentalOnda1IntegrationTest.TestConfig.class)
class DentalOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("e2000000-0000-0000-0000-000000000116");
    private static final UUID INSTANCE = UUID.fromString("e2100000-0000-0000-0000-000000000116");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private DentalReminderJob job;
    @Autowired
    private ConfirmacaoConsultaHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID patientId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dental')",
            COMPANY, "Dental Onda1", "dental-onda1");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-dt1", "tok-dt1");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Vini')",
            contactId, COMPANY, "+5511999990421");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        patientId = jdbcTemplate.queryForObject(
            "insert into dental_patients (company_id, name, contact_id) values (?, 'Vini', ?) returning id",
            UUID.class, COMPANY, contactId);
    }

    private UUID seedAppointment(String status, Instant startAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into dental_appointments (id, company_id, patient_id, conversation_id, start_at, "
                + "duration_minutes, end_at, type, status) values (?, ?, ?, ?, ?, 30, ?, 'Limpeza', ?)",
            id, COMPANY, patientId, conversationId,
            Timestamp.from(startAt), Timestamp.from(startAt.plus(30, ChronoUnit.MINUTES)), status);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("consulta amanhã → lembrete SIM 1x (rearm ao remarcar); confirmação com barreira via paciente")
    void reminderAndConfirmacao() {
        UUID a = seedAppointment("agendada", tomorrowAt(14));

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("AMANHÃ").contains("SIM");
        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        String tag = "Confirmo!\n<confirmacao_consulta>{\"appointment_id\":\"" + a
            + "\"}</confirmacao_consulta>";
        // barreira: contato divergente.
        assertThat(confirmacaoHandler.parseAndConfirm(COMPANY, conversationId, UUID.randomUUID(), tag))
            .isEmpty();
        assertThat(confirmacaoHandler.parseAndConfirm(COMPANY, conversationId, contactId, tag)).isPresent();
        assertThat(statusOf(a)).isEqualTo("confirmada");
    }

    @Test
    @DisplayName("auto-realizada: confirmada vencida → realizada; agendada não")
    void autoComplete() {
        Instant past = Instant.now().minus(5, ChronoUnit.HOURS);
        UUID confirmada = seedAppointment("confirmada", past);
        UUID agendada = seedAppointment("agendada", past);

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmada)).isEqualTo("realizada");
        assertThat(statusOf(agendada)).isEqualTo("agendada");
    }

    @Test
    @DisplayName("recall: OFF por default; ON → paciente sem consulta há 6m recebe 1 convite; futura suprime")
    void recall_optIn() {
        seedAppointment("realizada", Instant.now().minus(210, ChronoUnit.DAYS));

        assertThat(job.runRecalls()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into dental_clinic_config (company_id, recall_enabled, recall_months) values (?, true, 6)",
            COMPANY);
        assertThat(job.runRecalls()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Vini").contains("revisão");

        fakeEvolution.reset();
        assertThat(job.runRecalls()).isZero();   // por episódio.

        jdbcTemplate.update("update dental_patients set recall_reminded_at = null where id = ?", patientId);
        seedAppointment("agendada", tomorrowAt(9));
        assertThat(job.runRecalls()).isZero();   // consulta futura suprime.
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from dental_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-dental-onda1";
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
