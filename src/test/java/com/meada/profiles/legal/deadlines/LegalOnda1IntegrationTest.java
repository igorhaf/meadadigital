package com.meada.profiles.legal.deadlines;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.legal.cases.LegalCaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Legal 1 (backlog #1/#3): lembrete de prazos/audiências D-3/D-1 (com
 * rearm ao remarcar + toggle) via {@link LegalDeadlineReminderJob}, e a mensagem de
 * pós-encerramento (agradecimento + avaliação + indicação) no ENCERRADO. EvolutionSender é um
 * FAKE. Trava jurídica intacta (texto só com data/local, sem mérito).
 */
@Import(LegalOnda1IntegrationTest.TestConfig.class)
class LegalOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("c0000000-0000-0000-0000-0000000000f2");
    private static final UUID INSTANCE = UUID.fromString("c0100000-0000-0000-0000-0000000000f2");
    private static final UUID USER = UUID.fromString("c0200000-0000-0000-0000-0000000000f2");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private LegalDeadlineReminderJob job;
    @Autowired
    private LegalCaseService caseService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID clientId;
    private UUID caseId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'legal')",
            COMPANY, "Legal Onda", "legal-onda");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@legal-onda.dev', 'admin')",
            USER, COMPANY);
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-leg", "tok-leg");
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Léo')",
            contactId, COMPANY, "+5511999990281");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", UUID.randomUUID(), COMPANY, contactId, INSTANCE);
        clientId = jdbcTemplate.queryForObject(
            "insert into legal_clients (company_id, name, contact_id) values (?, 'Léo Autor', ?) returning id",
            UUID.class, COMPANY, contactId);
        caseId = jdbcTemplate.queryForObject(
            "insert into legal_cases (company_id, legal_client_id, cnj_number, title) "
                + "values (?, ?, '07102331520258070019', 'Ação Trabalhista') returning id",
            UUID.class, COMPANY, clientId);
    }

    private UUID seedDeadline(String kind, LocalDate dueDate) {
        return jdbcTemplate.queryForObject(
            "insert into legal_deadlines (company_id, case_id, kind, title, due_date, due_time, location) "
                + "values (?, ?, ?, 'Audiência de instrução', ?, '14:30', 'Fórum Central') returning id",
            UUID.class, COMPANY, caseId, kind, Date.valueOf(dueDate));
    }

    @Test
    @DisplayName("prazo em D-3 e D-1 → lembrete 1x por janela; remarcar REARMA; sem mérito no texto")
    void reminderWindows() {
        LocalDate today = LocalDate.now(SP);
        UUID d3 = seedDeadline("audiencia", today.plusDays(3));
        UUID d1 = seedDeadline("prazo", today.plusDays(1));

        assertThat(job.runReminders()).isEqualTo(2);
        assertThat(fakeEvolution.sent()).hasSize(2);
        String textoAudiencia = fakeEvolution.sent().stream()
            .map(SentMessage::text).filter(t -> t.contains("AUDIÊNCIA")).findFirst().orElseThrow();
        assertThat(textoAudiencia).contains("14:30").contains("Fórum Central").contains("Ação Trabalhista");
        assertThat(textoAudiencia).doesNotContain("mérito").doesNotContain("chance");

        // idempotente na mesma janela.
        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        // remarcar o D-1 pra D+3 REARMA (marker ≠ nova due_date).
        jdbcTemplate.update("update legal_deadlines set due_date = ? where id = ?",
            Date.valueOf(today.plusDays(3)), d1);
        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);

        // prazo cumprido não lembra (o d3 entraria na janela D-1 daqui a 2 dias, mas testamos status).
        jdbcTemplate.update("update legal_deadlines set status = 'cumprido' where id = ?", d3);
        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();
    }

    @Test
    @DisplayName("toggle deadline_reminder_enabled=false silencia o job")
    void reminderToggleOff() {
        seedDeadline("prazo", LocalDate.now(SP).plusDays(1));
        jdbcTemplate.update(
            "insert into legal_config (company_id, deadline_reminder_enabled) values (?, false)", COMPANY);
        assertThat(job.runReminders()).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("ENCERRADO com pós-encerramento ON → 2 mensagens (status + agradecimento com review link)")
    void postClosure() {
        jdbcTemplate.update(
            "insert into legal_config (company_id, review_link) values (?, 'https://g.page/r/escritorio')",
            COMPANY);
        caseService.updateStatus(COMPANY, USER, caseId, "encerrado");

        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(0).text()).contains("ENCERRADO");
        String pos = fakeEvolution.sent().get(1).text();
        assertThat(pos).contains("Obrigado pela confiança")
            .contains("https://g.page/r/escritorio")
            .contains("indicação");

        // toggle OFF: outro processo encerrado só manda a notificação de status.
        jdbcTemplate.update("update legal_config set post_closure_enabled = false where company_id = ?",
            COMPANY);
        UUID case2 = jdbcTemplate.queryForObject(
            "insert into legal_cases (company_id, legal_client_id, cnj_number, title) "
                + "values (?, ?, '07102331520258070020', 'Ação Dois') returning id",
            UUID.class, COMPANY, clientId);
        fakeEvolution.reset();
        caseService.updateStatus(COMPANY, USER, case2, "encerrado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).doesNotContain("avaliação");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-legal-onda";
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
