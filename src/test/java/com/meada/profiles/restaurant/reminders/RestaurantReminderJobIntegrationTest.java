package com.meada.profiles.restaurant.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.restaurant.reservations.ConfirmacaoReservaHandler;
import com.meada.profiles.restaurant.reservations.Reservation;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Restaurant 1 (backlog #1/#3) contra PostgreSQL real: lembrete D-1 +
 * auto-transição via {@link RestaurantReminderJob} e o loop de confirmação via
 * {@link ConfirmacaoReservaHandler} (tag {@code <confirmacao_reserva>}, barreira de contato).
 * EvolutionSender é um FAKE.
 */
@Import(RestaurantReminderJobIntegrationTest.TestConfig.class)
class RestaurantReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("ab000000-0000-0000-0000-0000000000f1");
    private static final UUID INSTANCE = UUID.fromString("ab100000-0000-0000-0000-0000000000f1");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private RestaurantReminderJob job;
    @Autowired
    private ConfirmacaoReservaHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID tableId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'restaurant')",
            COMPANY, "Mesa Reminder", "mesa-reminder");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-rst", "tok-rst");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Rui')",
            contactId, COMPANY, "+5511999990221");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        tableId = jdbcTemplate.queryForObject(
            "insert into restaurant_tables (company_id, label, capacity) values (?, 'Mesa 1', 4) returning id",
            UUID.class, COMPANY);
    }

    private UUID seedReservation(String status, Instant startAt, UUID conversation, UUID contact) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into table_reservations (id, company_id, table_id, conversation_id, contact_id, "
                + "guest_name, start_at, duration_minutes, end_at, num_people, status) "
                + "values (?, ?, ?, ?, ?, 'Rui', ?, 120, ?, 4, ?)",
            id, COMPANY, tableId, conversation, contact,
            java.sql.Timestamp.from(startAt),
            java.sql.Timestamp.from(startAt.plus(120, ChronoUnit.MINUTES)), status);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("pendente de amanhã → lembrete SIM/NÃO 1x + idempotente; toggle off → nada")
    void reminder_onceAndToggle() {
        seedReservation("pendente", tomorrowAt(20), conversationId, contactId);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Mesa 1").contains("SIM");

        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        // toggle off → reserva nova de amanhã não lembra.
        jdbcTemplate.update(
            "insert into restaurant_reservation_config (company_id, reminder_enabled) values (?, false)",
            COMPANY);
        seedReservation("confirmada", tomorrowAt(21), conversationId, contactId);
        assertThat(job.runReminders()).isZero();
    }

    @Test
    @DisplayName("reserva manual sem conversa → marca sem envio")
    void noChannel_markedWithoutSend() {
        UUID r = seedReservation("pendente", tomorrowAt(19), null, null);
        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).isEmpty();
        Boolean reminded = jdbcTemplate.queryForObject(
            "select reminded_24h from table_reservations where id = ?", Boolean.class, r);
        assertThat(reminded).isTrue();
    }

    @Test
    @DisplayName("auto-transição: confirmada passada (2h+ de folga) → realizada, silenciosa; pendente não")
    void autoComplete() {
        Instant past = Instant.now().minus(5, ChronoUnit.HOURS);
        UUID confirmada = seedReservation("confirmada", past, conversationId, contactId);
        UUID pendente = seedReservation("pendente", past, conversationId, contactId);

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmada)).isEqualTo("realizada");
        assertThat(statusOf(pendente)).isEqualTo("pendente");
        assertThat(fakeEvolution.sent()).isEmpty();   // realizada é silenciosa.
    }

    @Test
    @DisplayName("<confirmacao_reserva>: SIM move pendente→confirmada; contato divergente é barrado")
    void confirmacaoTag_withContactBarrier() {
        UUID r = seedReservation("pendente", tomorrowAt(20), conversationId, contactId);
        String tag = "Perfeito! <confirmacao_reserva>{\"reservation_id\":\"" + r
            + "\",\"decisao\":\"confirmada\"}</confirmacao_reserva>";

        // contato divergente → barreira (nada muda).
        Optional<Reservation> barred = confirmacaoHandler.parseAndApply(
            COMPANY, conversationId, UUID.randomUUID(), tag);
        assertThat(barred).isEmpty();
        assertThat(statusOf(r)).isEqualTo("pendente");

        // contato certo → confirma (e a notificação padrão de confirmada dispara).
        Optional<Reservation> ok = confirmacaoHandler.parseAndApply(
            COMPANY, conversationId, contactId, tag);
        assertThat(ok).isPresent();
        assertThat(statusOf(r)).isEqualTo("confirmada");

        // NÃO → cancela (libera o slot).
        String cancel = "<confirmacao_reserva>{\"reservation_id\":\"" + r
            + "\",\"decisao\":\"cancelada\"}</confirmacao_reserva>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, cancel)).isPresent();
        assertThat(statusOf(r)).isEqualTo("cancelada");
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from table_reservations where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-restaurant-reminder";
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
