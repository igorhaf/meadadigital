package com.meada.profiles.cursos.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.cursos.certificates.CursosCertificatePublicController;
import com.meada.profiles.cursos.enrollments.CursosEnrollmentService;
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

/**
 * Integration test da onda Cursos 1 (backlog #1/#2/#3): certificado emitido na conclusão +
 * verificação pública, nudge anti-abandono por episódio e cupom na matrícula (inválido não
 * aborta). EvolutionSender é um FAKE.
 */
@Import(CursosOnda1IntegrationTest.TestConfig.class)
class CursosOnda1IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("f3000000-0000-0000-0000-000000000117");
    private static final UUID INSTANCE = UUID.fromString("f3100000-0000-0000-0000-000000000117");

    @Autowired
    private CursosEnrollmentService enrollmentService;
    @Autowired
    private CursosNudgeJob nudgeJob;
    @Autowired
    private CursosCertificatePublicController publicController;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;
    private UUID courseId;
    private UUID module1;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'cursos')",
            COMPANY, "Escola Onda1", "cursos-onda1");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-cr1", "tok-cr1");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Davi')",
            contactId, COMPANY, "+5511999990431");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        courseId = jdbcTemplate.queryForObject(
            "insert into cursos_courses (company_id, title, monthly_cents) "
                + "values (?, 'Violão do Zero', 9900) returning id",
            UUID.class, COMPANY);
        module1 = jdbcTemplate.queryForObject(
            "insert into cursos_modules (company_id, course_id, position, title, content) "
                + "values (?, ?, 0, 'Acordes básicos', 'conteúdo...') returning id",
            UUID.class, COMPANY, courseId);
        jdbcTemplate.update(
            "insert into cursos_modules (company_id, course_id, position, title, content) "
                + "values (?, ?, 1, 'Ritmos', 'conteúdo...')", COMPANY, courseId);
    }

    @Test
    @DisplayName("cupom válido desconta a mensalidade snapshotada; inválido não aborta")
    void coupon() {
        jdbcTemplate.update(
            "insert into cursos_coupons (company_id, code, kind, value) values (?, 'PRIMEIRA30', 'percent', 30)",
            COMPANY);
        var com = enrollmentService.create(COMPANY, courseId, contactId, conversationId,
            'D' + "avi", null, "primeira30", null);
        assertThat(com.discountCents()).isEqualTo(2970);
        assertThat(com.couponCode()).isEqualTo("PRIMEIRA30");

        UUID contact2 = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Bia')",
            contact2, COMPANY, "+5511999990432");
        var sem = enrollmentService.create(COMPANY, courseId, contact2, conversationId,
            "Bia", null, "NAOEXISTE", null);
        assertThat(sem.discountCents()).isZero();
    }

    @Test
    @DisplayName("conclusão emite certificado (idempotente) + notificação com código; verificação pública renderiza")
    void certificate() {
        jdbcTemplate.update(
            "insert into cursos_config (company_id, certificate_base_url) values (?, 'https://escola.local/')",
            COMPANY);
        var enrollment = enrollmentService.create(COMPANY, courseId, contactId, conversationId,
            "Davi", null, null, null);
        fakeEvolution.reset();
        enrollmentService.updateStatus(COMPANY, enrollment.id(), "concluida");

        String code = jdbcTemplate.queryForObject(
            "select code from cursos_certificates where enrollment_id = ?", String.class, enrollment.id());
        assertThat(code).isNotBlank();
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("CERTIFICADO") && t.contains("/public/cursos/certificados/" + code));

        var html = publicController.verify(code);
        assertThat(html.getStatusCode().value()).isEqualTo(200);
        assertThat(html.getBody()).contains("Davi").contains("Violão do Zero").contains(code);

        assertThat(publicController.verify("XXXX-XXXX-XXXX").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("nudge: matrícula parada além da janela → 1 toque por episódio; progresso rearma")
    void nudge() {
        var enrollment = enrollmentService.create(COMPANY, courseId, contactId, conversationId,
            "Davi", null, null, null);
        jdbcTemplate.update(
            "update cursos_enrollments set created_at = now() - interval '10 days' where id = ?",
            enrollment.id());

        fakeEvolution.reset();
        assertThat(nudgeJob.runNudges()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("0/2").contains("Acordes básicos");

        fakeEvolution.reset();
        assertThat(nudgeJob.runNudges()).isZero();   // 1 por episódio.

        // progresso avança DEPOIS do nudge → rearma (após nova janela de inatividade).
        jdbcTemplate.update(
            "update cursos_enrollments set nudge_sent_at = now() - interval '20 days' where id = ?",
            enrollment.id());
        jdbcTemplate.update(
            "insert into cursos_enrollment_progress (enrollment_id, module_id, completed_at) "
                + "values (?, ?, now() - interval '9 days')", enrollment.id(), module1);
        assertThat(nudgeJob.runNudges()).isEqualTo(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("1/2").contains("Ritmos");

        // toggle off.
        jdbcTemplate.update(
            "insert into cursos_config (company_id, nudge_enabled) values (?, false) "
                + "on conflict (company_id) do update set nudge_enabled = false", COMPANY);
        jdbcTemplate.update("update cursos_enrollments set nudge_sent_at = null where id = ?", enrollment.id());
        fakeEvolution.reset();
        assertThat(nudgeJob.runNudges()).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-cursos-onda1";
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
