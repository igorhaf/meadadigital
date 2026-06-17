package com.meada.whatsapp.profiles.academia.classes;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.academia.classes.AcademiaClassService.ClassInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AcademiaClassService (camada 7.7): create+audit, delete em uso → 409, list por dia.
 */
class AcademiaClassServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaClassService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia C", "academia-c");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@aca-c.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita academia_class_created")
    void create_persistsAndAudits() {
        AcademiaClass c = service.create(COMPANY, USER, "Funcional", "funcional", 1, LocalTime.of(7, 0), 60, 12, "Carla");
        assertThat(c.name()).isEqualTo("Funcional");
        assertThat(c.dayOfWeek()).isEqualTo(1);
        assertThat(c.capacity()).isEqualTo(12);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'academia_class_created' and entity_id = ?",
            Long.class, c.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("list por dia da semana filtra corretamente")
    void list_byDay() {
        service.create(COMPANY, USER, "Seg", "funcional", 1, LocalTime.of(7, 0), 60, 12, null);
        service.create(COMPANY, USER, "Ter", "pilates", 2, LocalTime.of(9, 0), 60, 8, null);
        List<AcademiaClass> mon = service.list(COMPANY, true, 1);
        assertThat(mon).hasSize(1);
        assertThat(mon.get(0).name()).isEqualTo("Seg");
    }

    @Test
    @DisplayName("delete de aula com matrícula → ClassInUseException (409)")
    void delete_inUse() {
        AcademiaClass c = service.create(COMPANY, USER, "Yoga", "yoga", 4, LocalTime.of(18, 30), 60, 10, null);
        // plano + matrícula + junction referenciando a aula.
        UUID plan = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'P', 15000)", plan, COMPANY);
        UUID memb = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_memberships (id, company_id, plan_id, student_name, plan_name, plan_monthly_cents) "
            + "values (?, ?, ?, 'Aluno', 'P', 15000)", memb, COMPANY, plan);
        jdbcTemplate.update("insert into academia_membership_classes (membership_id, class_id, class_name_snapshot, "
            + "class_day_of_week_snapshot, class_start_time_snapshot, class_duration_minutes_snapshot, class_modality_snapshot) "
            + "values (?, ?, 'Yoga', 4, '18:30', 60, 'yoga')", memb, c.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, c.id())).isInstanceOf(ClassInUseException.class);
    }
}
