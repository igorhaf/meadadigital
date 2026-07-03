package com.meada.profiles.barbearia.reminders;

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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test dos jobs da barbearia (onda 1, backlog #1/#7) contra PostgreSQL real, sem o
 * scheduler (chama a lógica pública direto). EvolutionSender é um FAKE.
 *
 * <p>Lembrete (#1): agendado nas próximas 24h → "confirma? SIM/CANCELAR" + reminded_24h marcado +
 * idempotente; toggle off / fora da janela / manual sem conversa → sem envio. Auto-transição (#7):
 * confirmado com end_at passado (+2h) → realizado (silencioso); ticket 'aguardando' de ontem →
 * expirado; toggle off → nada.
 */
@Import(BarberJobsIntegrationTest.TestConfig.class)
class BarberJobsIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-0000000000b3");
    private static final UUID INSTANCE = UUID.fromString("cb100000-0000-0000-0000-0000000000b3");

    @Autowired
    private BarberReminderJob reminderJob;
    @Autowired
    private BarberAutoTransitionJob autoTransitionJob;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID barberId;
    private UUID serviceId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia Jobs", "barbearia-jobs");
        barberId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')",
            barberId, COMPANY);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes, price_cents) "
            + "values (?, ?, 'Corte', 30, 4000)", serviceId, COMPANY);
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-jb", "tok-jb");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Cliente')",
            contactId, COMPANY, "+5511999990184");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
    }

    /** Insere um agendamento direto no banco (start relativo a agora) e devolve o id. */
    private UUID seedAppointment(String status, Instant startAt, UUID conversation) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_appointments (id, company_id, barber_id, service_id, conversation_id, "
                + "contact_id, guest_name, start_at, duration_minutes, end_at, service_name, barber_name, "
                + "price_cents, status) values (?, ?, ?, ?, ?, ?, 'Cliente', ?, 30, ?, 'Corte', 'Marcelo', 4000, ?)",
            id, COMPANY, barberId, serviceId, conversation, contactId,
            java.sql.Timestamp.from(startAt), java.sql.Timestamp.from(startAt.plusSeconds(1800)), status);
        return id;
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject("select status from barber_appointments where id = ?", String.class, id);
    }

    private boolean remindedOf(UUID id) {
        Boolean b = jdbcTemplate.queryForObject("select reminded_24h from barber_appointments where id = ?", Boolean.class, id);
        return Boolean.TRUE.equals(b);
    }

    @Test
    @DisplayName("#1 agendado nas próximas 24h → lembrete SIM/CANCELAR enviado + marcado + idempotente")
    void reminder_sentOnceThenIdempotent() {
        UUID a = seedAppointment("agendado", Instant.now().plus(Duration.ofHours(3)), conversationId);

        assertThat(reminderJob.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("SIM").contains("CANCELAR").contains("Corte");
        assertThat(remindedOf(a)).isTrue();
        assertThat(statusOf(a)).isEqualTo("agendado");   // quem muda o status é a RESPOSTA do cliente.

        fakeEvolution.reset();
        assertThat(reminderJob.runReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("#1 toggle desligado / fora da janela de 24h → nada; manual sem conversa → marca sem envio")
    void reminder_edgeCases() {
        jdbcTemplate.update("insert into barber_config (company_id, reminder_enabled) values (?, false)", COMPANY);
        UUID muted = seedAppointment("agendado", Instant.now().plus(Duration.ofHours(3)), conversationId);
        assertThat(reminderJob.runReminders()).isZero();

        // religa: o 'muted' volta a ser elegível (nunca foi lembrado) + o manual entra; o de 30h não.
        jdbcTemplate.update("update barber_config set reminder_enabled = true where company_id = ?", COMPANY);
        UUID far = seedAppointment("agendado", Instant.now().plus(Duration.ofHours(30)), conversationId);
        UUID manual = seedAppointment("agendado", Instant.now().plus(Duration.ofHours(3)), null);
        assertThat(reminderJob.runReminders()).isEqualTo(2);
        assertThat(fakeEvolution.sent()).hasSize(1);     // só o 'muted' tem canal; manual marca sem enviar.
        assertThat(remindedOf(muted)).isTrue();
        assertThat(remindedOf(manual)).isTrue();
        assertThat(remindedOf(far)).isFalse();
    }

    @Test
    @DisplayName("#7 confirmado com end_at passado (+2h) → realizado (silencioso); futuro não é tocado")
    void autoTransition_confirmedPastBecomesRealized() {
        UUID past = seedAppointment("confirmado", Instant.now().minus(Duration.ofHours(5)), conversationId);
        UUID future = seedAppointment("confirmado", Instant.now().plus(Duration.ofHours(5)), conversationId);

        int touched = autoTransitionJob.runAutoTransitions();

        assertThat(touched).isEqualTo(1);
        assertThat(statusOf(past)).isEqualTo("realizado");
        assertThat(statusOf(future)).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).isEmpty();      // realizado é silencioso.
    }

    @Test
    @DisplayName("#7 ticket 'aguardando' de ONTEM → expirado; de hoje → intacto; toggle off → nada")
    void autoTransition_expiresStaleTickets() {
        UUID stale = UUID.randomUUID();
        UUID fresh = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_queue_tickets (id, company_id, service_id, guest_name, service_name, "
                + "duration_minutes, status, enqueued_at) values (?, ?, ?, 'Cliente', 'Corte', 30, "
                + "'aguardando', now() - interval '1 day')",
            stale, COMPANY, serviceId);
        jdbcTemplate.update(
            "insert into barber_queue_tickets (id, company_id, service_id, guest_name, service_name, "
                + "duration_minutes, status, enqueued_at) values (?, ?, ?, 'Cliente', 'Corte', 30, "
                + "'aguardando', now())",
            fresh, COMPANY, serviceId);

        assertThat(autoTransitionJob.runAutoTransitions()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select status from barber_queue_tickets where id = ?", String.class, stale))
            .isEqualTo("expirado");
        assertThat(jdbcTemplate.queryForObject("select status from barber_queue_tickets where id = ?", String.class, fresh))
            .isEqualTo("aguardando");

        // toggle off → mais nada acontece (novo ticket velho fica aguardando).
        jdbcTemplate.update("insert into barber_config (company_id, auto_complete_enabled) values (?, false)", COMPANY);
        UUID stale2 = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_queue_tickets (id, company_id, service_id, guest_name, service_name, "
                + "duration_minutes, status, enqueued_at) values (?, ?, ?, 'Cliente', 'Corte', 30, "
                + "'aguardando', now() - interval '2 days')",
            stale2, COMPANY, serviceId);
        assertThat(autoTransitionJob.runAutoTransitions()).isZero();
        assertThat(jdbcTemplate.queryForObject("select status from barber_queue_tickets where id = ?", String.class, stale2))
            .isEqualTo("aguardando");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-barber-jobs";
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
