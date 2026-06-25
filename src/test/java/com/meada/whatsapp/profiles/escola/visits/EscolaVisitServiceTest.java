package com.meada.whatsapp.profiles.escola.visits;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.InvalidPeriodException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.escola.visits.EscolaVisitService.PastDateException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o EscolaVisitService (camada 8.19, ESCAPADA 2 = agenda LEVE dia+período, sem conflito):
 *
 * <ul>
 *   <li>agendar com data futura + período → agendada + notifica confirmação;</li>
 *   <li>data passada → PastDateException; período inválido → InvalidPeriodException;</li>
 *   <li>student_id nullable → OK sem aluno;</li>
 *   <li>SEM conflito: 2 visitas no mesmo dia+período → ambas OK;</li>
 *   <li>transição agendada→realizada/cancelada OK; inválida → InvalidStatusTransitionException;
 *       cancelada notifica.</li>
 * </ul>
 */
@Import(EscolaVisitServiceTest.TestConfig.class)
class EscolaVisitServiceTest extends AbstractIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private EscolaVisitService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000006");
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola V", "escola-v");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990600", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("agendar com data futura + período → agendada + notifica confirmação (sem aluno)")
    void create_ok() {
        LocalDate future = LocalDate.now(ZONE).plusDays(5);
        EscolaVisit v = service.create(COMPANY, conversationId, contactId, null, "Maria", "+5511999990600",
            future, "manha", 2, null);
        assertThat(v.status()).isEqualTo("agendada");
        assertThat(v.visitDate()).isEqualTo(future);
        assertThat(v.period()).isEqualTo("manha");
        assertThat(v.studentId()).isNull();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("agendada");
    }

    @Test
    @DisplayName("data passada → PastDateException")
    void create_pastDate() {
        LocalDate past = LocalDate.now(ZONE).minusDays(1);
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, null, "Maria", null,
            past, "manha", null, null))
            .isInstanceOf(PastDateException.class);
    }

    @Test
    @DisplayName("período inválido → InvalidPeriodException")
    void create_invalidPeriod() {
        LocalDate future = LocalDate.now(ZONE).plusDays(3);
        assertThatThrownBy(() -> service.create(COMPANY, conversationId, contactId, null, "Maria", null,
            future, "noite", null, null))
            .isInstanceOf(InvalidPeriodException.class);
    }

    @Test
    @DisplayName("SEM conflito: 2 visitas no mesmo dia+período → ambas OK")
    void create_noConflict() {
        LocalDate future = LocalDate.now(ZONE).plusDays(7);
        EscolaVisit v1 = service.create(COMPANY, conversationId, contactId, null, "Maria", null, future, "tarde", null, null);
        EscolaVisit v2 = service.create(COMPANY, conversationId, contactId, null, "Maria", null, future, "tarde", null, null);
        assertThat(v1.status()).isEqualTo("agendada");
        assertThat(v2.status()).isEqualTo("agendada");
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from escola_visits where company_id = ? and visit_date = ? and period = 'tarde'",
            Long.class, COMPANY, java.sql.Date.valueOf(future));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("transição agendada→cancelada OK + notifica; agendada→realizada OK (silenciosa)")
    void transitions() {
        LocalDate future = LocalDate.now(ZONE).plusDays(4);
        EscolaVisit a = service.create(COMPANY, conversationId, contactId, null, "Maria", null, future, "manha", null, null);
        fakeEvolution.reset();   // descarta a notificação de agendamento.
        EscolaVisit cancelled = service.updateStatus(COMPANY, a.id(), "cancelada");
        assertThat(cancelled.status()).isEqualTo("cancelada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelada");

        EscolaVisit b = service.create(COMPANY, conversationId, contactId, null, "Maria", null, future, "tarde", null, null);
        fakeEvolution.reset();
        EscolaVisit done = service.updateStatus(COMPANY, b.id(), "realizada");
        assertThat(done.status()).isEqualTo("realizada");
        assertThat(fakeEvolution.sent()).isEmpty();   // realizada é silenciosa.
    }

    @Test
    @DisplayName("transição inválida (realizada→cancelada) → InvalidStatusTransitionException")
    void invalidTransition() {
        LocalDate future = LocalDate.now(ZONE).plusDays(2);
        EscolaVisit a = service.create(COMPANY, conversationId, contactId, null, "Maria", null, future, "manha", null, null);
        service.updateStatus(COMPANY, a.id(), "realizada");
        assertThatThrownBy(() -> service.updateStatus(COMPANY, a.id(), "cancelada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-vis";
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
