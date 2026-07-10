package com.meada.profiles.estetica.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.estetica.appointments.ConfirmacaoEsteticaHandler;
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
 * Integration test da onda Estética 1 (backlog #1/#2/#3/#4): lembrete de véspera SIM/NÃO com
 * rearm, confirmação via tag (cancelar DEVOLVE a sessão ao pacote), auto-transições
 * (realizado/expirado) e régua de renovação (opt-in OFF; esgotado + a-vencer). EvolutionSender é
 * um FAKE. Trava estética intacta (tudo operacional).
 */
@Import(EsteticaOnda1IntegrationTest.TestConfig.class)
class EsteticaOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("c3000000-0000-0000-0000-000000000108");
    private static final UUID INSTANCE = UUID.fromString("c3100000-0000-0000-0000-000000000108");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private EsteticaReminderJob job;
    @Autowired
    private ConfirmacaoEsteticaHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID profId;
    private UUID procedureId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'estetica')",
            COMPANY, "Estetica Onda", "estetica-onda");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-est", "tok-est");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Marina')",
            contactId, COMPANY, "+5511999990341");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_professionals (id, company_id, name) values (?, ?, 'Camila')",
            profId, COMPANY);
        procedureId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_procedures (id, company_id, name, duration_minutes, unit_price_cents) "
            + "values (?, ?, 'Drenagem', 50, 12000)", procedureId, COMPANY);
    }

    private UUID seedAppointment(String status, Instant startAt, UUID packageId, boolean consumed) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into aesthetic_appointments (id, company_id, professional_id, procedure_id, package_id, "
                + "conversation_id, contact_id, guest_name, start_at, duration_minutes, end_at, "
                + "procedure_name, professional_name, consumed_session, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'Marina', ?, 50, ?, 'Drenagem', 'Camila', ?, ?)",
            id, COMPANY, profId, procedureId, packageId, conversationId, contactId,
            Timestamp.from(startAt), Timestamp.from(startAt.plus(50, ChronoUnit.MINUTES)),
            consumed, status);
        return id;
    }

    private UUID seedPackage(String status, int total, int used, LocalDate validUntil) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into aesthetic_packages (id, company_id, contact_id, conversation_id, procedure_id, "
                + "customer_name, procedure_name, unit_price_cents, total_sessions, sessions_used, "
                + "sessions_remaining, total_cents, status, activated_at, valid_until) "
                + "values (?, ?, ?, ?, ?, 'Marina', 'Drenagem', 12000, ?, ?, ?, ?, ?, now(), ?)",
            id, COMPANY, contactId, conversationId, procedureId, total, used, total - used,
            total * 12000, status, validUntil == null ? null : java.sql.Date.valueOf(validUntil));
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("sessão amanhã → lembrete SIM/NÃO 1x + rearm; NÃO cancela e DEVOLVE a sessão ao pacote")
    void reminderAndConfirmacao() {
        UUID pkg = seedPackage("ativo", 10, 3, null);   // 7 restantes
        UUID a = seedAppointment("agendado", tomorrowAt(14), pkg, true);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("AMANHÃ").contains("SIM");
        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();   // idempotente.

        // barreira de contato.
        String cancel = "<confirmacao_estetica>{\"appointment_id\":\"" + a
            + "\",\"decisao\":\"cancelado\"}</confirmacao_estetica>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), cancel))
            .isEmpty();

        // NÃO → cancelado; a sessão consumida volta ao pacote (mecânica existente).
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, cancel)).isPresent();
        assertThat(statusOf(a)).isEqualTo("cancelado");
        Integer remaining = jdbcTemplate.queryForObject(
            "select sessions_remaining from aesthetic_packages where id = ?", Integer.class, pkg);
        assertThat(remaining).isEqualTo(8);   // devolvida.

        // SIM em outra sessão → confirmado.
        UUID b = seedAppointment("agendado", tomorrowAt(16), null, false);
        String sim = "<confirmacao_estetica>{\"appointment_id\":\"" + b
            + "\",\"decisao\":\"confirmado\"}</confirmacao_estetica>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, sim)).isPresent();
        assertThat(statusOf(b)).isEqualTo("confirmado");
    }

    @Test
    @DisplayName("auto-transições: confirmado vencido → realizado; ativo com valid_until vencida → expirado")
    void autoTransitions() {
        Instant past = Instant.now().minus(5, ChronoUnit.HOURS);
        UUID confirmado = seedAppointment("confirmado", past, null, false);
        UUID agendado = seedAppointment("agendado", past, null, false);
        UUID vencido = seedPackage("ativo", 10, 2, LocalDate.now(SP).minusDays(1));
        UUID vigente = seedPackage("ativo", 10, 2, LocalDate.now(SP).plusDays(30));
        UUID semValidade = seedPackage("ativo", 10, 2, null);

        assertThat(job.runAutoComplete()).isEqualTo(1);
        assertThat(statusOf(confirmado)).isEqualTo("realizado");
        assertThat(statusOf(agendado)).isEqualTo("agendado");

        assertThat(job.runAutoExpire()).isEqualTo(1);
        assertThat(pkgStatusOf(vencido)).isEqualTo("expirado");
        assertThat(pkgStatusOf(vigente)).isEqualTo("ativo");
        assertThat(pkgStatusOf(semValidade)).isEqualTo("ativo");
        assertThat(fakeEvolution.sent()).isEmpty();   // tudo silencioso.
    }

    @Test
    @DisplayName("régua: OFF por default; ON → esgotado antigo e a-vencer recebem 1 toque cada")
    void renewals_optIn() {
        UUID esgotado = seedPackage("esgotado", 10, 10, null);
        jdbcTemplate.update(
            "update aesthetic_packages set status_updated_at = now() - interval '45 days' where id = ?",
            esgotado);
        // o a-vencer é de OUTRO contato — um pacote ativo do MESMO contato suprime o sweep de
        // esgotado (comportamento correto: quem já recomprou não recebe convite de renovação).
        UUID contact2 = UUID.randomUUID();
        UUID conversation2 = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Bela')",
            contact2, COMPANY, "+5511999990342");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversation2, COMPANY, contact2, INSTANCE);
        UUID aVencer = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into aesthetic_packages (id, company_id, contact_id, conversation_id, procedure_id, "
                + "customer_name, procedure_name, unit_price_cents, total_sessions, sessions_used, "
                + "sessions_remaining, total_cents, status, activated_at, valid_until) "
                + "values (?, ?, ?, ?, ?, 'Bela', 'Drenagem', 12000, 10, 5, 5, 120000, 'ativo', now(), ?)",
            aVencer, COMPANY, contact2, conversation2, procedureId,
            java.sql.Date.valueOf(LocalDate.now(SP).plusDays(3)));

        assertThat(job.runRenewals()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into aesthetic_config (company_id, renewal_enabled, renewal_days, expiry_warning_days) "
                + "values (?, true, 30, 7)", COMPANY);
        assertThat(job.runRenewals()).isEqualTo(2);
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("renovar"))
            .anyMatch(t -> t.contains("perto de vencer"));

        // 1 toque por pacote.
        fakeEvolution.reset();
        assertThat(job.runRenewals()).isZero();
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from aesthetic_appointments where id = ?", String.class, id);
    }

    private String pkgStatusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from aesthetic_packages where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-estetica-onda";
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
