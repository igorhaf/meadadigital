package com.meada.whatsapp.profiles.atelie.artisans;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.atelie.artisans.AtelieArtisanService.ArtisanInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AtelieArtisanService (camada 8.14): create+audit, toggle, delete em uso → 409
 * artisan_in_use (artisan_id é ON DELETE SET NULL na proposta — uso checado por hasProposals).
 * Clone do EventPlannerServiceTest.
 */
class AtelieArtisanServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AtelieArtisanService service;

    private static final UUID COMPANY = UUID.fromString("a7000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("b7000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Teste", "atelie-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@atelie.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita atelie_artisan_created")
    void create_persistsAndAudits() {
        AtelieArtisan a = service.create(COMPANY, USER, "Beatriz", "costura sob medida", null);
        assertThat(a.name()).isEqualTo("Beatriz");
        assertThat(a.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'atelie_artisan_created' and entity_id = ?",
            Long.class, a.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        AtelieArtisan a = service.create(COMPANY, USER, "Rodrigo", "arte", null);
        AtelieArtisan off = service.toggle(COMPANY, USER, a.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de artesão atribuído a proposta → ArtisanInUseException (409)")
    void delete_inUse() {
        AtelieArtisan a = service.create(COMPANY, USER, "Beatriz", "costura sob medida", null);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990170", "Cliente");
        jdbcTemplate.update(
            "insert into atelie_proposals (company_id, contact_id, artisan_id, customer_name, project_type, total_cents, status) "
                + "values (?, ?, ?, 'Cliente', 'costura', 0, 'rascunho')",
            COMPANY, contactId, a.id());

        assertThatThrownBy(() -> service.delete(COMPANY, USER, a.id()))
            .isInstanceOf(ArtisanInUseException.class);
    }
}
