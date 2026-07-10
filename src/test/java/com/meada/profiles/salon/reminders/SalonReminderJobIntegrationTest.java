package com.meada.profiles.salon.reminders;

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
 * Integration test do {@link SalonReminderJob} (onda Salon 1, backlog #1/#7) contra PostgreSQL
 * real. Lógica via métodos públicos (sem scheduler); EvolutionSender é um FAKE. Cobre: lembrete de
 * véspera 1x + idempotência + rearme ao remarcar; toggle off; sem canal → marca sem envio;
 * auto-transição opt-in (default OFF; ligada → confirmado passado vira realizado, silencioso).
 */
@Import(SalonReminderJobIntegrationTest.TestConfig.class)
class SalonReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("aa000000-0000-0000-0000-0000000000e1");
    private static final UUID INSTANCE = UUID.fromString("aa100000-0000-0000-0000-0000000000e1");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private SalonReminderJob job;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID professionalId;
    private UUID serviceId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'salon')",
            COMPANY, "Salon Reminder", "salon-reminder");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-slr", "tok-slr");
        UUID contact = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Bia')",
            contact, COMPANY, "+5511999990211");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contact, INSTANCE);
        professionalId = jdbcTemplate.queryForObject(
            "insert into salon_professionals (company_id, name) values (?, 'Ana') returning id",
            UUID.class, COMPANY);
        serviceId = jdbcTemplate.queryForObject(
            "insert into salon_offerings (company_id, name, duration_minutes) values (?, 'Corte', 45) returning id",
            UUID.class, COMPANY);
    }

    private UUID seedAppointment(String status, Instant startAt, UUID conversation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into salon_appointments (id, company_id, professional_id, service_id, conversation_id, "
                + "guest_name, start_at, duration_minutes, end_at, service_name, professional_name, status) "
                + "values (?, ?, ?, ?, ?, 'Bia', ?, 45, ?, 'Corte', 'Ana', ?)",
            id, COMPANY, professionalId, serviceId, conversation,
            java.sql.Timestamp.from(startAt),
            java.sql.Timestamp.from(startAt.plus(45, ChronoUnit.MINUTES)), status);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("agendado de amanhã → lembrete 1x + idempotente; REMARCAR rearma")
    void dueTomorrow_remindedOnceThenRearmedByReschedule() {
        UUID appt = seedAppointment("agendado", tomorrowAt(15), conversationId);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Corte").contains("Ana").contains("amanhã");

        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        // remarcado pra outra hora de amanhã → rearma.
        Instant novo = tomorrowAt(17);
        jdbcTemplate.update("update salon_appointments set start_at = ?, end_at = ? where id = ?",
            java.sql.Timestamp.from(novo), java.sql.Timestamp.from(novo.plus(45, ChronoUnit.MINUTES)), appt);
        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
    }

    @Test
    @DisplayName("toggle desligado / hoje / status terminal → nada")
    void notDue_nothing() {
        jdbcTemplate.update(
            "insert into salon_config (company_id, opens_at, closes_at, reminder_enabled) "
                + "values (?, '09:00', '20:00', false)", COMPANY);
        seedAppointment("agendado", tomorrowAt(15), conversationId);   // toggle off
        assertThat(job.runReminders()).isZero();

        jdbcTemplate.update("update salon_config set reminder_enabled = true where company_id = ?", COMPANY);
        seedAppointment("cancelado", tomorrowAt(16), conversationId);  // status terminal
        // HOJE fixo ao meio-dia (não Instant.now()+2h — depois das 22h BRT cruzaria a meia-noite
        // e viraria "amanhã", flake real de 2026-07-04).
        Instant hoje = LocalDate.now(SP).atTime(LocalTime.NOON).atZone(SP).toInstant();
        seedAppointment("confirmado", hoje, conversationId);           // hoje, não amanhã
        // o de amanhã 15h (toggle religado) dispara — os outros dois não.
        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
    }

    @Test
    @DisplayName("agendamento manual sem conversa → marca sem envio")
    void noChannel_markedWithoutSend() {
        UUID appt = seedAppointment("confirmado", tomorrowAt(11), null);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).isEmpty();
        java.sql.Timestamp marked = jdbcTemplate.queryForObject(
            "select reminded_start_at from salon_appointments where id = ?",
            java.sql.Timestamp.class, appt);
        assertThat(marked).isNotNull();
    }

    @Test
    @DisplayName("auto-transição: default OFF → nada; ON → confirmado passado vira realizado (silencioso)")
    void autoComplete_optIn() {
        Instant passado = Instant.now().minus(3, ChronoUnit.HOURS);
        UUID confirmado = seedAppointment("confirmado", passado, conversationId);
        UUID agendado = seedAppointment("agendado", passado, conversationId);

        // sem config (default OFF) → nada muda.
        assertThat(job.runAutoComplete()).isZero();

        jdbcTemplate.update(
            "insert into salon_config (company_id, opens_at, closes_at, auto_complete_enabled) "
                + "values (?, '09:00', '20:00', true)", COMPANY);
        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmado)).isEqualTo("realizado");
        assertThat(statusOf(agendado)).isEqualTo("agendado");   // falta é julgamento humano.
        assertThat(fakeEvolution.sent()).isEmpty();             // realizado é silencioso.
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from salon_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-salon-reminder";
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
