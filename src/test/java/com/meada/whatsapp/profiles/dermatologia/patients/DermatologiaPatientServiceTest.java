package com.meada.whatsapp.profiles.dermatologia.patients;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientService.ContactNotFoundException;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientService.PatientInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o DermatologiaPatientService (camada 8.11): create+audit, contato inexistente →
 * ContactNotFoundException, archive (active=false), delete em uso → PatientInUseException.
 */
class DermatologiaPatientServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DermatologiaPatientService service;

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000003");
    private static final UUID USER = UUID.fromString("d2000000-0000-0000-0000-000000000003");
    private UUID contactId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma A", "derma-a");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@derma-a.dev', 'admin')",
            USER, COMPANY);
        contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990180", "Marina");
    }

    @Test
    @DisplayName("create válido → persiste + audita dermatologia_patient_created")
    void create_persistsAndAudits() {
        DermatologiaPatient p = service.create(COMPANY, USER, contactId, "Marina", LocalDate.of(1990, 5, 20), null);
        assertThat(p.name()).isEqualTo("Marina");
        assertThat(p.birthDate()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'dermatologia_patient_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create com contato inexistente → ContactNotFoundException")
    void create_unknownContact() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, UUID.randomUUID(), "Marina", null, null))
            .isInstanceOf(ContactNotFoundException.class);
    }

    @Test
    @DisplayName("archive → active=false")
    void archive() {
        DermatologiaPatient p = service.create(COMPANY, USER, contactId, "Marina", null, null);
        DermatologiaPatient archived = service.archive(COMPANY, USER, p.id());
        assertThat(archived.active()).isFalse();
    }

    @Test
    @DisplayName("delete de paciente com consulta → PatientInUseException")
    void delete_inUse() {
        DermatologiaPatient p = service.create(COMPANY, USER, contactId, "Marina", null, null);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name) values (?, ?, 'Carla')",
            prof, COMPANY);
        UUID type = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) values (?, ?, 'Consulta', 30)",
            type, COMPANY);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into dermatologia_appointments (company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Marina', 'Carla', 'Consulta', 30, ?, ?, 'agendada')",
            COMPANY, prof, p.id(), type, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(PatientInUseException.class);
    }
}
