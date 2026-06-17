package com.meada.whatsapp.profiles.academia.plans;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.academia.plans.AcademiaPlanService.PlanInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AcademiaPlanService (camada 7.7): create+audit, delete em uso → 409.
 */
class AcademiaPlanServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaPlanService service;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("dc000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia Teste", "academia-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@aca.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita academia_plan_created")
    void create_persistsAndAudits() {
        AcademiaPlan p = service.create(COMPANY, USER, "Mensal Livre", 20000, "acesso total");
        assertThat(p.name()).isEqualTo("Mensal Livre");
        assertThat(p.monthlyCents()).isEqualTo(20000);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'academia_plan_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("delete de plano com matrícula → PlanInUseException (409)")
    void delete_inUse() {
        AcademiaPlan p = service.create(COMPANY, USER, "Mensal", 15000, null);
        jdbcTemplate.update(
            "insert into academia_memberships (company_id, plan_id, student_name, plan_name, plan_monthly_cents) "
                + "values (?, ?, 'Aluno', 'Mensal', 15000)", COMPANY, p.id());
        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id())).isInstanceOf(PlanInUseException.class);
    }
}
