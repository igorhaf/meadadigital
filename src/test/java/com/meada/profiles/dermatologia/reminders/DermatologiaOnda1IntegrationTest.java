package com.meada.profiles.dermatologia.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.dermatologia.appointments.ConfirmacaoDermaHandler;
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
 * Integration test da onda Dermatologia 1 (backlog #1/#2/#5): lembrete D-1 com envio do PREPARO
 * verbatim + confirmação via tag (barreira de contato), auto-transição confirmada vencida →
 * realizada e recall de retorno (opt-in OFF, por episódio). EvolutionSender é um FAKE. Trava
 * clínica intacta (textos administrativos; preparo verbatim do médico).
 */
@Import(DermatologiaOnda1IntegrationTest.TestConfig.class)
class DermatologiaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("e5000000-0000-0000-0000-000000000110");
    private static final UUID INSTANCE = UUID.fromString("e5100000-0000-0000-0000-000000000110");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private DermatologiaReminderJob job;
    @Autowired
    private ConfirmacaoDermaHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID profId;
    private UUID patientId;
    private UUID typeComPreparo;
    private UUID typeSemPreparo;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma Onda", "derma-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-drm", "tok-drm");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Iara')",
            contactId, COMPANY, "+5511999990361");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        profId = jdbcTemplate.queryForObject(
            "insert into dermatologia_professionals (company_id, name) values (?, 'Dra. Lis') returning id",
            UUID.class, COMPANY);
        patientId = jdbcTemplate.queryForObject(
            "insert into dermatologia_patients (company_id, contact_id, name) values (?, ?, 'Iara') returning id",
            UUID.class, COMPANY, contactId);
        typeComPreparo = jdbcTemplate.queryForObject(
            "insert into dermatologia_procedure_types (company_id, name, duration_minutes, prep_instructions) "
                + "values (?, 'Peeling', 40, 'Suspender ácidos 5 dias antes. Vir sem maquiagem.') returning id",
            UUID.class, COMPANY);
        typeSemPreparo = jdbcTemplate.queryForObject(
            "insert into dermatologia_procedure_types (company_id, name, duration_minutes) "
                + "values (?, 'Consulta', 30) returning id",
            UUID.class, COMPANY);
    }

    private UUID seedAppointment(String status, Instant startAt, UUID typeId, String typeName) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into dermatologia_appointments (id, company_id, professional_id, professional_name, "
                + "patient_id, patient_name, procedure_type_id, procedure_type_name, contact_id, "
                + "conversation_id, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, 'Dra. Lis', ?, 'Iara', ?, ?, ?, ?, 40, ?, ?, ?)",
            id, COMPANY, profId, patientId, typeId, typeName, contactId, conversationId,
            Timestamp.from(startAt), Timestamp.from(startAt.plus(40, ChronoUnit.MINUTES)), status);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("consulta amanhã → lembrete + PREPARO verbatim junto; sem preparo só lembrete; confirmação com barreira")
    void reminderWithPrep() {
        UUID comPreparo = seedAppointment("agendada", tomorrowAt(14), typeComPreparo, "Peeling");
        seedAppointment("agendada", tomorrowAt(16), typeSemPreparo, "Consulta");

        assertThat(job.runReminders()).isEqualTo(2);
        // 2 lembretes + 1 nota de preparo verbatim = 3 mensagens.
        assertThat(fakeEvolution.sent()).hasSize(3);
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.equals("Suspender ácidos 5 dias antes. Vir sem maquiagem."))
            .anyMatch(t -> t.contains("Peeling") && t.contains("AMANHÃ"));

        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();   // idempotente.

        // confirmação: barreira + SIM.
        String sim = "<confirmacao_derma>{\"appointment_id\":\"" + comPreparo
            + "\",\"decisao\":\"confirmada\"}</confirmacao_derma>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), sim)).isEmpty();
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, sim)).isPresent();
        assertThat(statusOf(comPreparo)).isEqualTo("confirmada");
    }

    @Test
    @DisplayName("auto-transição: confirmada vencida → realizada; agendada não (falta segue humana)")
    void autoComplete() {
        Instant past = Instant.now().minus(5, ChronoUnit.HOURS);
        UUID confirmada = seedAppointment("confirmada", past, typeSemPreparo, "Consulta");
        UUID agendada = seedAppointment("agendada", past, typeSemPreparo, "Consulta");

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmada)).isEqualTo("realizada");
        assertThat(statusOf(agendada)).isEqualTo("agendada");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("recall: OFF por default; ON → paciente inativo recebe 1 convite por episódio; consulta futura suprime")
    void recall_optIn() {
        seedAppointment("realizada", Instant.now().minus(210, ChronoUnit.DAYS), typeSemPreparo, "Consulta");

        assertThat(job.runRecalls()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into dermatologia_config (company_id, recall_enabled, recall_months) values (?, true, 6)",
            COMPANY);
        assertThat(job.runRecalls()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Iara").contains("reavaliação");

        // 1 por episódio.
        fakeEvolution.reset();
        assertThat(job.runRecalls()).isZero();

        // consulta futura ativa suprime mesmo re-armado.
        jdbcTemplate.update("update dermatologia_patients set recall_reminded_at = null where id = ?", patientId);
        seedAppointment("agendada", tomorrowAt(9), typeSemPreparo, "Consulta");
        assertThat(job.runRecalls()).isZero();
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from dermatologia_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-derma-onda";
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
