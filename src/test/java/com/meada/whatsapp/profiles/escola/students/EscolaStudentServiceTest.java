package com.meada.whatsapp.profiles.escola.students;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentService.ContactNotFoundException;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentService.StudentInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o EscolaStudentService (camada 8.19), sub-entidade do responsável (contact): create+audit,
 * contato inexistente → ContactNotFoundException, list por responsável (contactId), archive
 * (active=false), delete em uso (matrícula) → StudentInUseException.
 */
class EscolaStudentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EscolaStudentService service;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000003");
    private static final UUID USER = UUID.fromString("de000000-0000-0000-0000-000000000003");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola A", "escola-a");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@escola-a.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990300", "Responsável");
    }

    @Test
    @DisplayName("create válido → persiste + audita escola_student_created")
    void create_persistsAndAudits() {
        EscolaStudent s = service.create(COMPANY, USER, contactId, "Lucas", LocalDate.of(2020, 5, 1), "Jardim I", null);
        assertThat(s.name()).isEqualTo("Lucas");
        assertThat(s.contactId()).isEqualTo(contactId);
        assertThat(s.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'escola_student_created' and entity_id = ?",
            Long.class, s.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com contato (responsável) inexistente → ContactNotFoundException")
    void create_unknownContact() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, UUID.randomUUID(), "Lucas", null, null, null))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("list por responsável (contactId) traz só os filhos daquele responsável")
    void list_byContact() {
        UUID other = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            other, COMPANY, "+5511999990301", "Outro");
        service.create(COMPANY, USER, contactId, "Lucas", null, null, null);
        service.create(COMPANY, USER, contactId, "Ana", null, null, null);  // irmão
        service.create(COMPANY, USER, other, "Pedro", null, null, null);    // de outro responsável

        List<EscolaStudent> filhos = service.list(COMPANY, contactId, null, null);
        assertThat(filhos).hasSize(2);
        assertThat(filhos).extracting(EscolaStudent::name).containsExactlyInAnyOrder("Lucas", "Ana");
    }

    @Test
    @DisplayName("archive → active=false")
    void archive() {
        EscolaStudent s = service.create(COMPANY, USER, contactId, "Mimi", null, null, null);
        EscolaStudent archived = service.archive(COMPANY, USER, s.id());
        assertThat(archived.active()).isFalse();
    }

    @Test
    @DisplayName("delete de aluno com matrícula → StudentInUseException")
    void delete_inUse() {
        EscolaStudent s = service.create(COMPANY, USER, contactId, "Lucas", null, null, null);
        UUID classId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_classes (id, company_id, name, grade, shift, capacity, monthly_cents) "
            + "values (?, ?, 'Jardim I', 'Infantil', 'manha', 20, 50000)", classId, COMPANY);
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Lucas', 'Jardim I', 'Infantil', 'manha', 50000, 'ativa')",
            COMPANY, classId, s.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, s.id()))
            .isInstanceOf(StudentInUseException.class);
    }
}
