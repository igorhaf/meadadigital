package com.meada.whatsapp.profiles.viagens.consultants;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.viagens.consultants.TravelConsultantService.ConsultantInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o TravelConsultantService (camada 8.18 / perfil viagens): create+audit, toggle, delete em uso
 * → 409. Espelho do EventPlannerServiceTest (chassi eventos 8.2).
 */
class TravelConsultantServiceTest extends AbstractIntegrationTest {

    @Autowired
    private TravelConsultantService service;

    private static final UUID COMPANY = UUID.fromString("ce100000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("df100000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'viagens')",
            COMPANY, "Viagens Teste", "viagens-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@viagens.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita travel_consultant_created")
    void create_persistsAndAudits() {
        TravelConsultant c = service.create(COMPANY, USER, "Beatriz", "internacional / lua-de-mel", null);
        assertThat(c.name()).isEqualTo("Beatriz");
        assertThat(c.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'travel_consultant_created' and entity_id = ?",
            Long.class, c.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        TravelConsultant c = service.create(COMPANY, USER, "Rodrigo", "nacional / cruzeiros", null);
        TravelConsultant off = service.toggle(COMPANY, USER, c.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de consultor com proposta → ConsultantInUseException (409)")
    void delete_inUse() {
        TravelConsultant c = service.create(COMPANY, USER, "Beatriz", "internacional", null);
        // consultant_id é ON DELETE SET NULL — uso é checado por hasProposals(): seed de uma proposta.
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999991270", "Cliente");
        jdbcTemplate.update(
            "insert into travel_proposals (company_id, contact_id, consultant_id, customer_name, status) "
                + "values (?, ?, ?, 'Cliente', 'rascunho')",
            COMPANY, contactId, c.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, c.id()))
            .isInstanceOf(ConsultantInUseException.class);
    }
}
