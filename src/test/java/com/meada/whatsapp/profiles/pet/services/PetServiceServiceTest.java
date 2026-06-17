package com.meada.whatsapp.profiles.pet.services;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.pet.services.PetServiceService.InvalidSpeciesException;
import com.meada.whatsapp.profiles.pet.services.PetServiceService.ServiceInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PetServiceService (camada 7.8): create+audit, species_restriction persistida, espécie
 * inválida → InvalidSpeciesException, toggle, delete em uso → 409.
 */
class PetServiceServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PetServiceService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pet')",
            COMPANY, "Pet O", "pet-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pet-o.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita pet_service_created")
    void create_persistsAndAudits() {
        PetService s = service.create(COMPANY, USER, "Banho", "Higiene", 60, 5000, null, null);
        assertThat(s.name()).isEqualTo("Banho");
        assertThat(s.durationMinutes()).isEqualTo(60);
        assertThat(s.priceCents()).isEqualTo(5000);
        assertThat(s.speciesRestriction()).isNull();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pet_service_created' and entity_id = ?",
            Long.class, s.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com species_restriction='gato' persiste a restrição")
    void create_withSpeciesRestriction() {
        PetService s = service.create(COMPANY, USER, "Tosa felina", "Estética", 45, 7000, "gato", null);
        assertThat(s.speciesRestriction()).isEqualTo("gato");
    }

    @Test
    @DisplayName("create com espécie inválida → InvalidSpeciesException")
    void create_invalidSpecies() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Banho", "Higiene", 60, 5000, "passaro", null))
            .isInstanceOf(InvalidSpeciesException.class);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        PetService s = service.create(COMPANY, USER, "Hidratação", "Estética", 30, 4000, null, null);
        PetService off = service.toggle(COMPANY, USER, s.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de serviço com agendamento → ServiceInUseException (409)")
    void delete_inUse() {
        PetService s = service.create(COMPANY, USER, "Tosa", "Estética", 90, 9000, null, null);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name) values (?, ?, 'Carla')",
            prof, COMPANY);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990071", "Tutor");
        UUID animal = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Rex', 'cao')",
            animal, COMPANY, contactId);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into pet_appointments (company_id, professional_id, professional_name, service_id, service_name, "
                + "animal_id, animal_name, animal_species, contact_id, tutor_name, start_at, duration_minutes, end_at, status) "
                + "values (?, ?, 'Carla', ?, 'Tosa', ?, 'Rex', 'cao', ?, 'Tutor', ?, 90, ?, 'agendado')",
            COMPANY, prof, s.id(), animal, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(5400)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, s.id()))
            .isInstanceOf(ServiceInUseException.class);
    }
}
