package com.meada.profiles.barbearia.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.barbearia.appointments.BarberAppointment;
import com.meada.profiles.barbearia.appointments.BarberAppointmentService;
import com.meada.profiles.barbearia.queue.BarberQueueService;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Barbearia 2 (backlog #2/#8/#9): reativação de inativo (opt-in OFF,
 * com cupom de retorno), conversão de ticket da fila em atendimento imediato (une fila e agenda)
 * e pós-corte com review link + cooldown por contato. EvolutionSender é um FAKE.
 */
@Import(BarbeariaOnda2IntegrationTest.TestConfig.class)
class BarbeariaOnda2IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("a7000000-0000-0000-0000-000000000112");
    private static final UUID INSTANCE = UUID.fromString("a7100000-0000-0000-0000-000000000112");

    @Autowired
    private BarberReactivationJob reactivationJob;
    @Autowired
    private BarberQueueService queueService;
    @Autowired
    private BarberAppointmentService appointmentService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID barberId;
    private UUID serviceId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barber Onda2", "barber-onda2");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-bb2", "tok-bb2");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Nico')",
            contactId, COMPANY, "+5511999990381");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        barberId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')",
            barberId, COMPANY);
        serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes, price_cents) "
            + "values (?, ?, 'Corte', 30, 5000)", serviceId, COMPANY);
    }

    private UUID seedAppointment(String status, Instant startAt) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into barber_appointments (id, company_id, barber_id, service_id, contact_id, "
                + "conversation_id, guest_name, barber_name, service_name, price_cents, duration_minutes, "
                + "start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, ?, 'Nico', 'Marcelo', 'Corte', 5000, 30, ?, ?, ?)",
            id, COMPANY, barberId, serviceId, contactId, conversationId,
            Timestamp.from(startAt), Timestamp.from(startAt.plus(30, ChronoUnit.MINUTES)), status);
        return id;
    }

    @Test
    @DisplayName("reativação: OFF por default; ON → inativo além do ciclo recebe 1 toque com cupom")
    void reactivation_optIn() {
        seedAppointment("realizado", Instant.now().minus(60, ChronoUnit.DAYS));

        assertThat(reactivationJob.runReactivation()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into barber_config (company_id, opens_at, closes_at, reactivation_enabled, "
                + "reactivation_days, reactivation_coupon_code) values (?, '09:00', '20:00', true, 45, 'VOLTA10')",
            COMPANY);
        jdbcTemplate.update(
            "insert into barber_coupons (company_id, code, kind, value) values (?, 'VOLTA10', 'percent', 10)",
            COMPANY);
        assertThat(reactivationJob.runReactivation()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Nico").contains("VOLTA10");

        // cooldown = janela; agendamento futuro suprime.
        fakeEvolution.reset();
        assertThat(reactivationJob.runReactivation()).isZero();
        jdbcTemplate.update("delete from barber_reactivation_log where company_id = ?", COMPANY);
        seedAppointment("agendado", Instant.now().plus(2, ChronoUnit.DAYS));
        assertThat(reactivationJob.runReactivation()).isZero();
    }

    @Test
    @DisplayName("fila → atendimento: ticket chamado converte em agendamento imediato e vira atendido")
    void convertQueueTicket() {
        UUID ticketId = jdbcTemplate.queryForObject(
            "insert into barber_queue_tickets (company_id, barber_id, service_id, contact_id, "
                + "conversation_id, guest_name, service_name, duration_minutes, barber_name, status, called_at) "
                + "values (?, ?, ?, ?, ?, 'Nico', 'Corte', 30, 'Marcelo', 'chamado', now()) returning id",
            UUID.class, COMPANY, barberId, serviceId, contactId, conversationId);

        BarberAppointment appt = queueService.convertToAppointment(COMPANY, ticketId, null);
        assertThat(appt.barberName()).isEqualTo("Marcelo");
        assertThat(appt.serviceName()).isEqualTo("Corte");
        String ticketStatus = jdbcTemplate.queryForObject(
            "select status from barber_queue_tickets where id = ?", String.class, ticketId);
        assertThat(ticketStatus).isEqualTo("atendido");

        // ticket "qualquer barbeiro" sem barberId explícito → 400 barber_required.
        UUID anyTicket = jdbcTemplate.queryForObject(
            "insert into barber_queue_tickets (company_id, service_id, contact_id, conversation_id, "
                + "guest_name, service_name, duration_minutes, status) "
                + "values (?, ?, ?, ?, 'Nico', 'Corte', 30, 'aguardando') returning id",
            UUID.class, COMPANY, serviceId, contactId, conversationId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> queueService.convertToAppointment(COMPANY, anyTicket, null))
            .isInstanceOf(BarberQueueService.BarberRequiredException.class);
    }

    @Test
    @DisplayName("pós-corte: OFF por default; ON → realizado pede avaliação 1x por cooldown de contato")
    void postReview() {
        UUID a = seedAppointment("confirmado", Instant.now().minus(2, ChronoUnit.HOURS));
        appointmentService.updateStatus(COMPANY, a, "realizado");
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .noneMatch(t -> t.contains("avaliação"));   // default OFF.

        jdbcTemplate.update(
            "insert into barber_config (company_id, opens_at, closes_at, post_review_enabled, review_link) "
                + "values (?, '09:00', '20:00', true, 'https://g.page/r/barber') "
                + "on conflict (company_id) do update set post_review_enabled = true, "
                + "review_link = 'https://g.page/r/barber'",
            COMPANY);
        UUID b = seedAppointment("confirmado", Instant.now().minus(1, ChronoUnit.HOURS));
        fakeEvolution.reset();
        appointmentService.updateStatus(COMPANY, b, "realizado");
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("https://g.page/r/barber"));

        // cooldown: outro corte realizado do MESMO contato não pede de novo.
        UUID c = seedAppointment("confirmado", Instant.now().minus(30, ChronoUnit.MINUTES));
        fakeEvolution.reset();
        appointmentService.updateStatus(COMPANY, c, "realizado");
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .noneMatch(t -> t.contains("https://g.page/r/barber"));
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-barber-onda2";
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
