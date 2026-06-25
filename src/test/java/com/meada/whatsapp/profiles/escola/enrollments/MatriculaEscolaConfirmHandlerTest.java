package com.meada.whatsapp.profiles.escola.enrollments;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o MatriculaEscolaConfirmHandler (camada 8.19): tag com student_id existente → matricula;
 * tag com new_student → cadastra o aluno (sub-entidade do responsável) E matricula no mesmo turno;
 * sem student_id nem new_student → empty; turma lotada / anti-dupla → empty + warn; sem tag → empty;
 * stripOrderTag remove a tag.
 */
class MatriculaEscolaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private MatriculaEscolaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000007");
    private UUID conversationId;
    private UUID contactId;
    private UUID classId;
    private UUID lucas;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola M", "escola-m");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990700", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        classId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Jardim I', 'Infantil', 'manha', 20, 50000)", classId, COMPANY);
        lucas = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Lucas')",
            lucas, COMPANY, contactId);
    }

    @Test
    @DisplayName("MODO student_id existente → cria matrícula ativa para o aluno informado")
    void parseAndCreate_existingStudent() {
        String aiText = "Perfeito! Matriculei o Lucas no Jardim I.\n"
            + "<matricula_escola>{\"class_id\":\"" + classId + "\",\"student_id\":\"" + lucas + "\"}</matricula_escola>";

        Optional<EscolaEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(e).isPresent();
        assertThat(e.get().status()).isEqualTo("ativa");
        assertThat(e.get().studentName()).isEqualTo("Lucas");
        assertThat(e.get().className()).isEqualTo("Jardim I");
    }

    @Test
    @DisplayName("MODO new_student → cadastra o aluno E matricula (count de alunos sobe)")
    void parseAndCreate_newStudent() {
        Long before = jdbcTemplate.queryForObject("select count(*) from escola_students where company_id = ?",
            Long.class, COMPANY);

        String aiText = "Cadastrei a Ana e já matriculei!\n"
            + "<matricula_escola>{\"class_id\":\"" + classId + "\",\"new_student\":{\"name\":\"Ana\","
            + "\"birth_date\":\"2020-03-10\",\"intended_grade\":\"Jardim I\"}}</matricula_escola>";

        Optional<EscolaEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(e).isPresent();
        assertThat(e.get().studentName()).isEqualTo("Ana");
        Long after = jdbcTemplate.queryForObject("select count(*) from escola_students where company_id = ?",
            Long.class, COMPANY);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("sem student_id nem new_student → Optional.empty (não criado)")
    void parseAndCreate_noStudentMode() {
        String aiText = "Pronto!\n<matricula_escola>{\"class_id\":\"" + classId + "\"}</matricula_escola>";
        Optional<EscolaEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(e).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from escola_enrollments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("turma inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidClass() {
        String aiText = "Matriculado!\n<matricula_escola>{\"class_id\":\"" + UUID.randomUUID()
            + "\",\"student_id\":\"" + lucas + "\"}</matricula_escola>";
        Optional<EscolaEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(e).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from escola_enrollments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("anti-dupla: aluno já matriculado nessa turma na tag → Optional.empty + warn")
    void parseAndCreate_alreadyActive() {
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Lucas', 'Jardim I', 'Infantil', 'manha', 50000, 'ativa')",
            COMPANY, classId, lucas);
        String aiText = "Matriculado!\n<matricula_escola>{\"class_id\":\"" + classId
            + "\",\"student_id\":\"" + lucas + "\"}</matricula_escola>";
        Optional<EscolaEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(e).isEmpty();
    }

    @Test
    @DisplayName("turma lotada na tag → Optional.empty + warn")
    void parseAndCreate_classFull() {
        UUID tiny = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Unica', 'Infantil', 'manha', 1, 50000)", tiny, COMPANY);
        UUID outro = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Outro')",
            outro, COMPANY, contactId);
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Outro', 'Unica', 'Infantil', 'manha', 50000, 'ativa')",
            COMPANY, tiny, outro);
        String aiText = "Matriculado!\n<matricula_escola>{\"class_id\":\"" + tiny
            + "\",\"student_id\":\"" + lucas + "\"}</matricula_escola>";
        Optional<EscolaEnrollment> e = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(e).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<EscolaEnrollment> e = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer matricular seu filho em qual turma?");
        assertThat(e).isEmpty();
    }

    @Test
    @DisplayName("stripOrderTag remove a tag <matricula_escola>")
    void stripOrderTag_removes() {
        String aiText = "Pronto!\n<matricula_escola>{\"class_id\":\"" + classId + "\",\"student_id\":\""
            + lucas + "\"}</matricula_escola>";
        assertThat(handler.hasOrderTag(aiText)).isTrue();
        String stripped = handler.stripOrderTag(aiText);
        assertThat(stripped).isEqualTo("Pronto!");
        assertThat(handler.hasOrderTag(stripped)).isFalse();
    }
}
