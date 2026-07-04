package com.meada.profiles.fotografia.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.fotografia.appointments.ConfirmacaoFotografiaHandler;
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
 * Integration test da onda Fotografia 1 (backlog #2/#3): lembrete D-2/D-1 com rearm ao remarcar,
 * loop de confirmação via {@link ConfirmacaoFotografiaHandler} (barreira de contato),
 * auto-transição confirmada vencida → realizada e ENTREGA NO PRAZO (link verbatim + entregue +
 * convite pós-entrega). EvolutionSender é um FAKE.
 */
@Import(FotografiaOnda1IntegrationTest.TestConfig.class)
class FotografiaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("f0000000-0000-0000-0000-000000000105");
    private static final UUID INSTANCE = UUID.fromString("f0100000-0000-0000-0000-000000000105");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private FotografiaReminderJob job;
    @Autowired
    private ConfirmacaoFotografiaHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID profId;
    private UUID pkgId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'fotografia')",
            COMPANY, "Foto Onda", "foto-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-fto", "tok-fto");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Rita')",
            contactId, COMPANY, "+5511999990311");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        profId = jdbcTemplate.queryForObject(
            "insert into fotografia_professionals (company_id, name) values (?, 'Carla') returning id",
            UUID.class, COMPANY);
        pkgId = jdbcTemplate.queryForObject(
            "insert into fotografia_packages (company_id, name, duration_minutes, price_cents, delivery_days) "
                + "values (?, 'Ensaio 1h', 60, 50000, 7) returning id",
            UUID.class, COMPANY);
    }

    private UUID seedSession(String status, Instant startAt, String deliveryLink, LocalDate dueDate) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into fotografia_session_appointments (id, company_id, professional_id, package_id, contact_id, "
                + "conversation_id, customer_name, professional_name, package_name, price_cents, duration_minutes, "
                + "delivery_days, start_at, end_at, delivery_due_date, delivery_link, status) "
                + "values (?, ?, ?, ?, ?, ?, 'Rita', 'Carla', 'Ensaio 1h', 50000, 60, 7, ?, ?, ?, ?, ?)",
            id, COMPANY, profId, pkgId, contactId, conversationId,
            Timestamp.from(startAt), Timestamp.from(startAt.plus(60, ChronoUnit.MINUTES)),
            java.sql.Date.valueOf(dueDate), deliveryLink, status);
        return id;
    }

    private Instant at(LocalDate day, int hour) {
        return day.atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("sessões em D-2 e D-1 → 1 lembrete cada; remarcar REARMA; confirmação via tag com barreira")
    void remindersAndConfirmacao() {
        LocalDate today = LocalDate.now(SP);
        UUID d2 = seedSession("agendada", at(today.plusDays(2), 14), null, today.plusDays(9));
        UUID d1 = seedSession("agendada", at(today.plusDays(1), 10), null, today.plusDays(8));

        assertThat(job.runReminders()).isEqualTo(2);
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("AMANHÃ"))
            .allMatch(t -> t.contains("Ensaio 1h") && t.contains("Carla"));

        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();   // idempotente por janela.

        // remarcar o D-1 pra outro horário REARMA a janela D-1.
        jdbcTemplate.update("update fotografia_session_appointments set start_at = ?, end_at = ? where id = ?",
            Timestamp.from(at(today.plusDays(1), 16)),
            Timestamp.from(at(today.plusDays(1), 17)), d1);
        assertThat(job.runReminders()).isEqualTo(1);

        // confirmação: contato divergente é barrado; o dono confirma (agendada → confirmada).
        String tag = "Confirmo!\n<confirmacao_foto>{\"session_id\":\"" + d2
            + "\",\"decisao\":\"confirmada\"}</confirmacao_foto>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), tag)).isEmpty();
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, tag)).isPresent();
        assertThat(statusOf(d2)).isEqualTo("confirmada");

        // cancelamento pela cliente também fecha o loop.
        String cancel = "<confirmacao_foto>{\"session_id\":\"" + d1
            + "\",\"decisao\":\"cancelada\"}</confirmacao_foto>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, cancel)).isPresent();
        assertThat(statusOf(d1)).isEqualTo("cancelada");
    }

    @Test
    @DisplayName("auto-transição: confirmada vencida → realizada (silenciosa); agendada não")
    void autoComplete() {
        Instant past = Instant.now().minus(5, ChronoUnit.HOURS);
        UUID confirmada = seedSession("confirmada", past, null, LocalDate.now(SP).plusDays(7));
        UUID agendada = seedSession("agendada", past, null, LocalDate.now(SP).plusDays(7));

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmada)).isEqualTo("realizada");
        assertThat(statusOf(agendada)).isEqualTo("agendada");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("entrega no prazo: realizada COM link e due → link VERBATIM + entregue + convite; sem link não")
    void autoDeliver() {
        LocalDate today = LocalDate.now(SP);
        Instant past = Instant.now().minus(10, ChronoUnit.DAYS);
        UUID comLink = seedSession("realizada", past, "https://galeria.example/rita", today);
        UUID semLink = seedSession("realizada", past, null, today);

        assertThat(job.runAutoDeliver()).isEqualTo(1);
        assertThat(statusOf(comLink)).isEqualTo("entregue");
        assertThat(statusOf(semLink)).isEqualTo("realizada");   // fica pro estúdio (painel destaca).
        assertThat(fakeEvolution.sent()).hasSize(2);            // link verbatim + convite pós-entrega.
        assertThat(fakeEvolution.sent().get(0).text()).isEqualTo("https://galeria.example/rita");
        assertThat(fakeEvolution.sent().get(1).text()).contains("fotos extras");

        // idempotente (status já entregue).
        fakeEvolution.reset();
        assertThat(job.runAutoDeliver()).isZero();

        // toggle off silencia.
        jdbcTemplate.update(
            "insert into fotografia_config (company_id, auto_deliver_enabled) values (?, false)", COMPANY);
        jdbcTemplate.update(
            "update fotografia_session_appointments set delivery_link = 'https://x.example' where id = ?",
            semLink);
        assertThat(job.runAutoDeliver()).isZero();
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from fotografia_session_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-foto-onda";
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
