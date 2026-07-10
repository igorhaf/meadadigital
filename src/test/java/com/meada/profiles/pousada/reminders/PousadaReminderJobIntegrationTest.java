package com.meada.profiles.pousada.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.pousada.reservations.ConfirmacaoPousadaHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Pousada 1 (backlog #2/#4): lembrete de check-in D-1 + auto-transição
 * (opt-in) via {@link PousadaReminderJob} e o loop de confirmação via
 * {@link ConfirmacaoPousadaHandler}. EvolutionSender é um FAKE.
 */
@Import(PousadaReminderJobIntegrationTest.TestConfig.class)
class PousadaReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("ac000000-0000-0000-0000-0000000000a2");
    private static final UUID INSTANCE = UUID.fromString("ac100000-0000-0000-0000-0000000000a2");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private PousadaReminderJob job;
    @Autowired
    private ConfirmacaoPousadaHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID roomId;
    private UUID contactId;
    private UUID conversationId;
    private final LocalDate today = LocalDate.now(SP);

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pousada')",
            COMPANY, "Pousada Reminder", "pousada-reminder");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-psd", "tok-psd");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Lia')",
            contactId, COMPANY, "+5511999990231");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        roomId = jdbcTemplate.queryForObject(
            "insert into pousada_rooms (company_id, name, capacity, nightly_rate_cents) "
                + "values (?, 'Suíte Mar', 2, 30000) returning id",
            UUID.class, COMPANY);
    }

    private UUID seedReservation(String status, LocalDate checkIn, LocalDate checkOut,
                                 UUID conversation, UUID contact) {
        UUID id = UUID.randomUUID();
        int nights = (int) (checkOut.toEpochDay() - checkIn.toEpochDay());
        jdbcTemplate.update(
            "insert into pousada_reservations (id, company_id, room_id, conversation_id, contact_id, "
                + "guest_name, guests_count, check_in_date, check_out_date, nights, room_name, "
                + "nightly_rate_cents, capacity_snapshot, total_cents, status) "
                + "values (?, ?, ?, ?, ?, 'Lia', 2, ?, ?, ?, 'Suíte Mar', 30000, 2, ?, ?)",
            id, COMPANY, roomId, conversation, contact,
            java.sql.Date.valueOf(checkIn), java.sql.Date.valueOf(checkOut), nights,
            30000 * nights, status);
        return id;
    }

    @Test
    @DisplayName("check-in amanhã → lembrete 1x + idempotente; REMARCAR rearma; toggle off → nada")
    void reminder_onceRearmToggle() {
        UUID r = seedReservation("reservado", today.plusDays(1), today.plusDays(4), conversationId, contactId);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Suíte Mar").contains("AMANHÃ");

        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        // datas remarcadas pra amanhã de novo? mesma data → não rearma; outra data que caia amanhã…
        jdbcTemplate.update("update pousada_reservations set reminded_checkin_date = ? where id = ?",
            java.sql.Date.valueOf(today.minusDays(10)), r);   // simulou remarcação (marcador antigo)
        assertThat(job.runReminders()).isEqualTo(1);

        jdbcTemplate.update(
            "insert into pousada_config (company_id, reminder_enabled) values (?, false)", COMPANY);
        seedReservation("confirmado", today.plusDays(1), today.plusDays(2), conversationId, contactId);
        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();
    }

    @Test
    @DisplayName("auto-transição: default OFF → nada; ON → no_show (check-in vencido) e checked_out")
    void autoTransitions_optIn() {
        UUID noShow = seedReservation("confirmado", today.minusDays(3), today.plusDays(1), conversationId, contactId);
        UUID out = seedReservation("checked_in", today.minusDays(5), today.minusDays(1), conversationId, contactId);

        assertThat(job.runAutoTransitions()).isZero();   // default OFF

        jdbcTemplate.update(
            "insert into pousada_config (company_id, auto_transition_enabled) values (?, true)", COMPANY);
        fakeEvolution.reset();
        assertThat(job.runAutoTransitions()).isEqualTo(2);
        assertThat(statusOf(noShow)).isEqualTo("no_show");
        assertThat(statusOf(out)).isEqualTo("checked_out");
        assertThat(fakeEvolution.sent()).isEmpty();   // ambas silenciosas.
    }

    @Test
    @DisplayName("<confirmacao_pousada>: confirma chegada com barreira de contato; cancelar libera")
    void confirmacaoTag() {
        UUID r = seedReservation("reservado", today.plusDays(1), today.plusDays(3), conversationId, contactId);
        String tag = "<confirmacao_pousada>{\"reservation_id\":\"" + r
            + "\",\"decisao\":\"confirmado\"}</confirmacao_pousada>";

        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), tag))
            .isEmpty();   // barreira de contato.
        assertThat(statusOf(r)).isEqualTo("reservado");

        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, tag)).isPresent();
        assertThat(statusOf(r)).isEqualTo("confirmado");

        String cancel = "<confirmacao_pousada>{\"reservation_id\":\"" + r
            + "\",\"decisao\":\"cancelado\"}</confirmacao_pousada>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, cancel)).isPresent();
        assertThat(statusOf(r)).isEqualTo("cancelado");
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from pousada_reservations where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-pousada-reminder";
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
