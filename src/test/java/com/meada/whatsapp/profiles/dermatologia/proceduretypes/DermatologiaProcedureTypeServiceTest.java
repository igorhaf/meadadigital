package com.meada.whatsapp.profiles.dermatologia.proceduretypes;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeService.InvalidDurationException;
import com.meada.whatsapp.profiles.dermatologia.proceduretypes.DermatologiaProcedureTypeService.ProcedureTypeInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o DermatologiaProcedureTypeService (camada 8.11, ESCAPADA): create+audit (duração+preparo),
 * duração inválida → InvalidDurationException, delete em uso → ProcedureTypeInUseException.
 */
class DermatologiaProcedureTypeServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DermatologiaProcedureTypeService service;

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000002");
    private static final UUID USER = UUID.fromString("d2000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma P", "derma-p");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@derma-p.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste (duração+preparo) + audita dermatologia_procedure_type_created")
    void create_persistsAndAudits() {
        DermatologiaProcedureType p = service.create(COMPANY, USER, "Botox", 60, "Suspender ácido 5 dias antes.", null);
        assertThat(p.name()).isEqualTo("Botox");
        assertThat(p.durationMinutes()).isEqualTo(60);
        assertThat(p.prepInstructions()).isEqualTo("Suspender ácido 5 dias antes.");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'dermatologia_procedure_type_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("create sem preparo → prep_instructions null")
    void create_noPrep() {
        DermatologiaProcedureType p = service.create(COMPANY, USER, "Consulta", 30, null, null);
        assertThat(p.prepInstructions()).isNull();
    }

    @Test
    @DisplayName("create com duração 4 (< 5) → InvalidDurationException")
    void create_invalidDurationLow() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Curta", 4, null, null))
            .isInstanceOf(InvalidDurationException.class);
    }

    @Test
    @DisplayName("create com duração 481 (> 480) → InvalidDurationException")
    void create_invalidDurationHigh() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, "Longa", 481, null, null))
            .isInstanceOf(InvalidDurationException.class);
    }

    @Test
    @DisplayName("delete de tipo com consulta → ProcedureTypeInUseException (409)")
    void delete_inUse() {
        DermatologiaProcedureType type = service.create(COMPANY, USER, "Consulta", 30, null, null);
        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, COMPANY);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990171", "Cliente");
        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Cliente')",
            patientId, COMPANY, contactId);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into dermatologia_appointments (company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Cliente', 'Carla', 'Consulta', 30, ?, ?, 'agendada')",
            COMPANY, prof, patientId, type.id(), contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, type.id()))
            .isInstanceOf(ProcedureTypeInUseException.class);
    }
}
