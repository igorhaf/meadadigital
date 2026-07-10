package com.meada.profiles.cursos.enrollments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService.CourseInactiveException;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService.InvalidStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o CursosEnrollmentService (camada 8.20 / perfil cursos) — os CRÍTICOS: create válida com
 * snapshots, curso inativo, anti-dupla por (contato, curso) (2ª ativa mesmo curso → 409
 * already_enrolled; OUTRO curso → OK), transições incl. concluida/cancelada materializam end_date +
 * notificam, e trancada silenciosa. EvolutionSender fake. Clone do AcademiaMembershipServiceTest
 * (camada 7.7).
 */
@Import(CursosEnrollmentServiceTest.TestConfig.class)
class CursosEnrollmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CursosEnrollmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cc100000-0000-0000-0000-000000000001");
    private UUID courseA;
    private UUID courseB;
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'cursos')",
            COMPANY, "Curso S", "curso-s");
        courseA = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, category, monthly_cents) "
            + "values (?, ?, 'Inglês Básico', 'idiomas', 15000)", courseA, COMPANY);
        courseB = UUID.randomUUID();
        jdbcTemplate.update("insert into cursos_courses (id, company_id, title, category, monthly_cents) "
            + "values (?, ?, 'Violão', 'música', 18000)", courseB, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999991100", "Aluno");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("create válida → ativa + snapshots do curso")
    void create_ok() {
        CursosEnrollment e = service.create(COMPANY, courseA, null, null, "Pedro", null, null, null);
        assertThat(e.status()).isEqualTo("ativa");
        assertThat(e.courseTitle()).isEqualTo("Inglês Básico");
        assertThat(e.courseMonthlyCents()).isEqualTo(15000);
    }

    @Test
    @DisplayName("create com curso inativo → CourseInactiveException")
    void create_courseInactive() {
        jdbcTemplate.update("update cursos_courses set active = false where id = ?", courseA);
        assertThatThrownBy(() -> service.create(COMPANY, courseA, null, null, "X", null, null, null))
            .isInstanceOf(CourseInactiveException.class);
    }

    @Test
    @DisplayName("anti-dupla: 2ª matrícula ATIVA no MESMO curso/contato → AlreadyEnrolledException")
    void create_alreadyEnrolledSameCourse() {
        service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null);
        assertThatThrownBy(() -> service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null))
            .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    @DisplayName("anti-dupla: matrícula em OUTRO curso do mesmo contato → OK (não conflita)")
    void create_otherCourseOk() {
        service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null);
        CursosEnrollment second = service.create(COMPANY, courseB, contactId, conversationId, "Aluno", null, null, null);
        assertThat(second.status()).isEqualTo("ativa");
        assertThat(second.courseTitle()).isEqualTo("Violão");
    }

    @Test
    @DisplayName("updateStatus ativa→cancelada → end_date materializado + notifica despedida")
    void cancel_endDateAndNotify() {
        CursosEnrollment e = service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null);
        fakeEvolution.reset();   // descarta a notificação de boas-vindas.
        CursosEnrollment cancelled = service.updateStatus(COMPANY, e.id(), "cancelada");
        assertThat(cancelled.status()).isEqualTo("cancelada");
        assertThat(cancelled.endDate()).isNotNull();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelada");
    }

    @Test
    @DisplayName("updateStatus ativa→concluida → end_date materializado + parabéns + certificado (onda 1)")
    void complete_endDateAndNotify() {
        CursosEnrollment e = service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null);
        fakeEvolution.reset();
        CursosEnrollment done = service.updateStatus(COMPANY, e.id(), "concluida");
        assertThat(done.status()).isEqualTo("concluida");
        assertThat(done.endDate()).isNotNull();
        assertThat(fakeEvolution.sent()).hasSize(2);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Parabéns");
        assertThat(fakeEvolution.sent().get(1).text()).contains("CERTIFICADO");
    }

    @Test
    @DisplayName("updateStatus ativa→trancada → silenciosa (nada enviado); end_date NULL")
    void lock_silent() {
        CursosEnrollment e = service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null);
        fakeEvolution.reset();
        CursosEnrollment locked = service.updateStatus(COMPANY, e.id(), "trancada");
        assertThat(locked.status()).isEqualTo("trancada");
        assertThat(locked.endDate()).isNull();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("updateStatus concluida→ativa (terminal) → InvalidStatusTransitionException")
    void invalidTransitionFromTerminal() {
        CursosEnrollment e = service.create(COMPANY, courseA, contactId, conversationId, "Aluno", null, null, null);
        service.updateStatus(COMPANY, e.id(), "concluida");
        assertThatThrownBy(() -> service.updateStatus(COMPANY, e.id(), "ativa"))
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
            return "key-cur";
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
