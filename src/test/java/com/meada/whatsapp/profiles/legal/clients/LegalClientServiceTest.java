package com.meada.whatsapp.profiles.legal.clients;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.legal.clients.LegalClientService.LegalClientInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testa o LegalClientService (camada 7.2): create+audit, update parcial, delete em uso → 409. */
class LegalClientServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LegalClientService service;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d8000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'legal')",
            COMPANY, "Adv Teste", "adv-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@adv.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita legal_client_created")
    void create_persistsAndAudits() {
        LegalClient c = service.create(COMPANY, USER, "Maria Silva", "maria@x.com", "+5511999998888",
            "12345678900", null, "cliente antigo");
        assertThat(c.name()).isEqualTo("Maria Silva");
        assertThat(c.email()).isEqualTo("maria@x.com");
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action='legal_client_created' and entity_id=?",
            Long.class, c.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("update parcial (só email) preserva o nome")
    void update_partial() {
        LegalClient c = service.create(COMPANY, USER, "João Santos", null, null, null, null, null);
        LegalClient up = service.update(COMPANY, USER, c.id(), null, "joao@novo.com", null, null,
            null, false, null);
        assertThat(up.email()).isEqualTo("joao@novo.com");
        assertThat(up.name()).isEqualTo("João Santos");
    }

    @Test
    @DisplayName("delete de cliente com processo → LegalClientInUseException (409)")
    void delete_inUse() {
        LegalClient c = service.create(COMPANY, USER, "Empresa Beta", null, null, null, null, null);
        jdbcTemplate.update(
            "insert into legal_cases (company_id, legal_client_id, cnj_number, title) "
                + "values (?, ?, '07102331520258070019', 'Ação X')", COMPANY, c.id());
        assertThatThrownBy(() -> service.delete(COMPANY, USER, c.id()))
            .isInstanceOf(LegalClientInUseException.class);
    }
}
