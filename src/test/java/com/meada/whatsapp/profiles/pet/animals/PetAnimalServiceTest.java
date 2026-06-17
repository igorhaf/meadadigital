package com.meada.whatsapp.profiles.pet.animals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.AnimalInUseException;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.ContactNotFoundException;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.InvalidSpeciesException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PetAnimalService (camada 7.8): create+audit, contato inexistente → ContactNotFoundException,
 * espécie inválida → InvalidSpeciesException, archive (active=false), delete em uso → AnimalInUseException.
 */
class PetAnimalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PetAnimalService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000003");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000003");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pet')",
            COMPANY, "Pet A", "pet-a");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pet-a.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990080", "Tutor");
    }

    @Test
    @DisplayName("create válido → persiste + audita pet_animal_created")
    void create_persistsAndAudits() {
        PetAnimal a = service.create(COMPANY, USER, contactId, "Rex", "cao", "Vira-lata", "macho", 2020, null);
        assertThat(a.name()).isEqualTo("Rex");
        assertThat(a.species()).isEqualTo("cao");
        assertThat(a.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pet_animal_created' and entity_id = ?",
            Long.class, a.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com contato inexistente → ContactNotFoundException")
    void create_unknownContact() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, UUID.randomUUID(), "Rex", "cao", null, null, null, null))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("create com espécie inválida → InvalidSpeciesException")
    void create_invalidSpecies() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, contactId, "Rex", "passaro", null, null, null, null))
            .isInstanceOf(InvalidSpeciesException.class);
    }

    @Test
    @DisplayName("archive → active=false")
    void archive() {
        PetAnimal a = service.create(COMPANY, USER, contactId, "Mimi", "gato", null, "femea", null, null);
        PetAnimal archived = service.archive(COMPANY, USER, a.id());
        assertThat(archived.active()).isFalse();
    }

    @Test
    @DisplayName("delete de animal com agendamento → AnimalInUseException")
    void delete_inUse() {
        PetAnimal a = service.create(COMPANY, USER, contactId, "Rex", "cao", null, "macho", null, null);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name) values (?, ?, 'Carla')",
            prof, COMPANY);
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, duration_minutes) values (?, ?, 'Banho', 60)",
            svc, COMPANY);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into pet_appointments (company_id, professional_id, professional_name, service_id, service_name, "
                + "animal_id, animal_name, animal_species, contact_id, tutor_name, start_at, duration_minutes, end_at, status) "
                + "values (?, ?, 'Carla', ?, 'Banho', ?, 'Rex', 'cao', ?, 'Tutor', ?, 60, ?, 'agendado')",
            COMPANY, prof, svc, a.id(), contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, a.id()))
            .isInstanceOf(AnimalInUseException.class);
    }
}
