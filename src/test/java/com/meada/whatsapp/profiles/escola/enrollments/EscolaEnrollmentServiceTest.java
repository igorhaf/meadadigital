package com.meada.whatsapp.profiles.escola.enrollments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.AlreadyActiveException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.ClassFullException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.ClassInactiveException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.InvalidStatusTransitionException;
import com.meada.whatsapp.profiles.escola.enrollments.EscolaEnrollmentService.StudentInactiveException;
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
 * Testa o EscolaEnrollmentService (camada 8.19, ESCAPADA 1 = matrícula/assinatura sobre aluno
 * sub-entidade). Clone do AcademiaMembershipServiceTest adaptado:
 *
 * <ul>
 *   <li>matrícula nasce ATIVA + boas-vindas (FakeEvolutionSender) + snapshots corretos
 *       (studentName/responsibleName/className/grade/shift/monthlyCents);</li>
 *   <li>CAPACITY TRANSACIONAL por turma: capacity N → N matrículas OK, a (N+1)ª → ClassFullException;</li>
 *   <li>ANTI-DUPLA por (aluno, turma): 2ª ativa do MESMO aluno na MESMA turma → AlreadyActiveException;
 *       o MESMO aluno em OUTRA turma → OK; OUTRO aluno (irmão) na mesma turma com vaga → OK;</li>
 *   <li>SUSPENSA mantém a vaga (capacity 1 + suspensa → nova matrícula ainda ClassFull); CANCELADA
 *       libera + materializa end_date; transição inválida → InvalidStatusTransitionException.</li>
 * </ul>
 */
@Import(EscolaEnrollmentServiceTest.TestConfig.class)
class EscolaEnrollmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EscolaEnrollmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000004");
    private UUID classManha;   // capacity 20
    private UUID classTarde;   // capacity 20
    private UUID lucas;        // aluno
    private UUID ana;          // irmão de Lucas (mesmo responsável)
    private UUID contactId;    // responsável
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola E", "escola-e");
        classManha = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Jardim I', 'Infantil', 'manha', 20, 50000)", classManha, COMPANY);
        classTarde = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Jardim II', 'Infantil', 'tarde', 20, 60000)", classTarde, COMPANY);

        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990400", "Maria (mãe)");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);

        lucas = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Lucas')",
            lucas, COMPANY, contactId);
        ana = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Ana')",
            ana, COMPANY, contactId);
    }

    @Test
    @DisplayName("create válida → ativa + boas-vindas + snapshots corretos")
    void create_ok() {
        EscolaEnrollment m = service.create(COMPANY, classManha, lucas, contactId, conversationId, null);
        assertThat(m.status()).isEqualTo("ativa");
        assertThat(m.studentName()).isEqualTo("Lucas");
        assertThat(m.responsibleName()).isEqualTo("Maria (mãe)");
        assertThat(m.className()).isEqualTo("Jardim I");
        assertThat(m.classGrade()).isEqualTo("Infantil");
        assertThat(m.classShift()).isEqualTo("manha");
        assertThat(m.classMonthlyCents()).isEqualTo(50000);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Lucas").contains("Jardim I");
    }

    @Test
    @DisplayName("create em turma inativa → ClassInactiveException")
    void create_classInactive() {
        jdbcTemplate.update("update escola_classes set active = false where id = ?", classManha);
        assertThatThrownBy(() -> service.create(COMPANY, classManha, lucas, contactId, conversationId, null))
            .isInstanceOf(ClassInactiveException.class);
    }

    @Test
    @DisplayName("create com aluno arquivado → StudentInactiveException")
    void create_studentInactive() {
        jdbcTemplate.update("update escola_students set active = false where id = ?", lucas);
        assertThatThrownBy(() -> service.create(COMPANY, classManha, lucas, contactId, conversationId, null))
            .isInstanceOf(StudentInactiveException.class);
    }

    @Test
    @DisplayName("CAPACITY TRANSACIONAL: capacity 2 → 2 matrículas OK, a 3ª → ClassFullException (carrega className)")
    void capacity_classFull() {
        UUID tiny = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Berçário', 'Bebê', 'integral', 2, 70000)", tiny, COMPANY);
        UUID a1 = newStudent("Aluno1");
        UUID a2 = newStudent("Aluno2");
        UUID a3 = newStudent("Aluno3");

        service.create(COMPANY, tiny, a1, null, null, null);   // 1/2
        service.create(COMPANY, tiny, a2, null, null, null);   // 2/2
        assertThatThrownBy(() -> service.create(COMPANY, tiny, a3, null, null, null))  // 3 → estoura
            .isInstanceOf(ClassFullException.class)
            .satisfies(e -> assertThat(((ClassFullException) e).className()).isEqualTo("Berçário"));
    }

    @Test
    @DisplayName("ANTI-DUPLA: mesma turma+mesmo aluno → AlreadyActive; mesmo aluno OUTRA turma → OK; irmão mesma turma → OK")
    void antiDupla() {
        // 1ª matrícula do Lucas em manhã.
        service.create(COMPANY, classManha, lucas, contactId, conversationId, null);
        // mesmo aluno (Lucas) + MESMA turma → anti-dupla.
        assertThatThrownBy(() -> service.create(COMPANY, classManha, lucas, contactId, conversationId, null))
            .isInstanceOf(AlreadyActiveException.class);
        // mesmo aluno (Lucas) em OUTRA turma → OK.
        EscolaEnrollment outra = service.create(COMPANY, classTarde, lucas, contactId, conversationId, null);
        assertThat(outra.status()).isEqualTo("ativa");
        // irmão (Ana) na MESMA turma (manhã, com vaga) → OK.
        EscolaEnrollment irmao = service.create(COMPANY, classManha, ana, contactId, conversationId, null);
        assertThat(irmao.status()).isEqualTo("ativa");
        assertThat(irmao.studentName()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("SUSPENSA mantém a vaga: capacity 1 + suspensa → nova matrícula ainda ClassFull; cancelada libera")
    void suspended_keepsSlot() {
        UUID tiny = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Unica', 'Infantil', 'manha', 1, 50000)", tiny, COMPANY);
        UUID ocupante = newStudent("Ocupante");
        UUID novo = newStudent("Novo");
        UUID liberado = newStudent("Liberado");

        EscolaEnrollment a = service.create(COMPANY, tiny, ocupante, null, null, null);
        // suspende → ainda ocupa a vaga.
        service.updateStatus(COMPANY, a.id(), "suspensa");
        assertThatThrownBy(() -> service.create(COMPANY, tiny, novo, null, null, null))
            .isInstanceOf(ClassFullException.class);
        // cancela → libera a vaga.
        service.updateStatus(COMPANY, a.id(), "cancelada");
        EscolaEnrollment freed = service.create(COMPANY, tiny, liberado, null, null, null);
        assertThat(freed.status()).isEqualTo("ativa");
    }

    @Test
    @DisplayName("updateStatus ativa→cancelada → end_date materializado + notifica despedida")
    void cancel_endDateAndNotify() {
        EscolaEnrollment m = service.create(COMPANY, classManha, lucas, contactId, conversationId, null);
        fakeEvolution.reset();   // descarta a notificação de boas-vindas.
        EscolaEnrollment cancelled = service.updateStatus(COMPANY, m.id(), "cancelada");
        assertThat(cancelled.status()).isEqualTo("cancelada");
        assertThat(cancelled.endDate()).isNotNull();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelada");
    }

    @Test
    @DisplayName("transição inválida (cancelada→ativa) → InvalidStatusTransitionException")
    void invalidTransition() {
        EscolaEnrollment m = service.create(COMPANY, classManha, lucas, contactId, conversationId, null);
        service.updateStatus(COMPANY, m.id(), "cancelada");
        assertThatThrownBy(() -> service.updateStatus(COMPANY, m.id(), "ativa"))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    private UUID newStudent(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, ?)",
            id, COMPANY, contactId, name);
        return id;
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-esc";
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
