package com.meada.whatsapp.profiles.casamento.planners;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.casamento.planners.WeddingPlannerService.PlannerInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o WeddingPlannerService (camada 8.7): create+audit, toggle, delete em uso → 409.
 * Clone do EventPlannerServiceTest.
 */
class WeddingPlannerServiceTest extends AbstractIntegrationTest {

    @Autowired
    private WeddingPlannerService service;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("bf000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'casamento')",
            COMPANY, "Casamento Teste", "casamento-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@casamento.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita wedding_planner_created")
    void create_persistsAndAudits() {
        WeddingPlanner p = service.create(COMPANY, USER, "Beatriz", "cerimonial completo", null);
        assertThat(p.name()).isEqualTo("Beatriz");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'wedding_planner_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        WeddingPlanner p = service.create(COMPANY, USER, "Rodrigo", "destination wedding", null);
        WeddingPlanner off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de assessor com proposta → PlannerInUseException (409)")
    void delete_inUse() {
        WeddingPlanner p = service.create(COMPANY, USER, "Beatriz", "cerimonial", null);
        // planner_id é ON DELETE SET NULL — uso é checado por hasProposals(): seed de uma proposta.
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990170", "Cliente");
        jdbcTemplate.update(
            "insert into wedding_proposals (company_id, contact_id, planner_id, customer_name, status) "
                + "values (?, ?, ?, 'Cliente', 'rascunho')",
            COMPANY, contactId, p.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(PlannerInUseException.class);
    }
}
