package com.meada.whatsapp.profiles.pet.professionals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.pet.professionals.PetProfessionalService.ProfessionalInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PetProfessionalService (camada 7.8): create+audit, toggle, delete em uso → 409.
 */
class PetProfessionalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PetProfessionalService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pet')",
            COMPANY, "Pet Teste", "pet-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pet.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita pet_professional_created")
    void create_persistsAndAudits() {
        PetProfessional p = service.create(COMPANY, USER, "Carla", "Veterinária", null);
        assertThat(p.name()).isEqualTo("Carla");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pet_professional_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        PetProfessional p = service.create(COMPANY, USER, "Patrícia", "Tosadora", null);
        PetProfessional off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de profissional com agendamento → ProfessionalInUseException (409)")
    void delete_inUse() {
        PetProfessional p = service.create(COMPANY, USER, "Júlia", "Banhista", null);
        // seed de um serviço + animal + agendamento referenciando o profissional (FK restrict).
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, duration_minutes) values (?, ?, 'Banho', 60)",
            svc, COMPANY);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990070", "Tutor");
        UUID animal = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Rex', 'cao')",
            animal, COMPANY, contactId);
        Instant start = Instant.parse("2026-07-01T15:00:00Z");
        jdbcTemplate.update(
            "insert into pet_appointments (company_id, professional_id, professional_name, service_id, service_name, "
                + "animal_id, animal_name, animal_species, contact_id, tutor_name, start_at, duration_minutes, end_at, status) "
                + "values (?, ?, 'Júlia', ?, 'Banho', ?, 'Rex', 'cao', ?, 'Tutor', ?, 60, ?, 'agendado')",
            COMPANY, p.id(), svc, animal, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(3600)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProfessionalInUseException.class);
    }
}
