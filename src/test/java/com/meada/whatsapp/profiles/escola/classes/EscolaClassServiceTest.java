package com.meada.whatsapp.profiles.escola.classes;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.escola.classes.EscolaClassService.ClassInUseException;
import com.meada.whatsapp.profiles.escola.classes.EscolaClassService.InvalidShiftException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o EscolaClassService (camada 8.19): create+audit, turno inválido → InvalidShiftException,
 * list por turno, delete de turma com matrícula → ClassInUseException (409 class_in_use).
 */
class EscolaClassServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EscolaClassService service;

    private static final UUID COMPANY = UUID.fromString("ce000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("de000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'escola')",
            COMPANY, "Escola C", "escola-c");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@escola-c.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita escola_class_created")
    void create_persistsAndAudits() {
        EscolaClass c = service.create(COMPANY, USER, "Jardim I", "Infantil", "manha", 20, 50000, 2026, "Turma manhã");
        assertThat(c.name()).isEqualTo("Jardim I");
        assertThat(c.shift()).isEqualTo("manha");
        assertThat(c.capacity()).isEqualTo(20);
        assertThat(c.monthlyCents()).isEqualTo(50000);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'escola_class_created' and entity_id = ?",
            Long.class, c.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com turno inválido → InvalidShiftException")
    void create_invalidShift() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "X", "Infantil", "noite", 20, 50000, null, null))
            .isInstanceOf(InvalidShiftException.class);
    }

    @Test
    @DisplayName("list por turno filtra corretamente")
    void list_byShift() {
        service.create(COMPANY, USER, "Manhã", "Infantil", "manha", 20, 50000, null, null);
        service.create(COMPANY, USER, "Tarde", "Infantil", "tarde", 20, 50000, null, null);
        List<EscolaClass> manha = service.list(COMPANY, true, "manha");
        assertThat(manha).hasSize(1);
        assertThat(manha.get(0).name()).isEqualTo("Manhã");
    }

    @Test
    @DisplayName("delete de turma com matrícula → ClassInUseException (409)")
    void delete_inUse() {
        EscolaClass c = service.create(COMPANY, USER, "Pré I", "Infantil", "integral", 10, 60000, null, null);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990200", "Responsável");
        UUID studentId = UUID.randomUUID();
        jdbcTemplate.update("insert into escola_students (id, company_id, contact_id, name) values (?, ?, ?, 'Aluno')",
            studentId, COMPANY, contactId);
        jdbcTemplate.update("insert into escola_enrollments (company_id, class_id, student_id, student_name, "
            + "class_name, class_grade, class_shift, class_monthly_cents, status) "
            + "values (?, ?, ?, 'Aluno', 'Pré I', 'Infantil', 'integral', 60000, 'ativa')",
            COMPANY, c.id(), studentId);

        assertThatThrownBy(() -> service.delete(COMPANY, USER, c.id())).isInstanceOf(ClassInUseException.class);
    }
}
