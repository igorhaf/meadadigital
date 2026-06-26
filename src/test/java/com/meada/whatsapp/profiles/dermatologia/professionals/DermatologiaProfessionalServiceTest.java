package com.meada.whatsapp.profiles.dermatologia.professionals;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.dermatologia.professionals.DermatologiaProfessionalService.ProfessionalInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o DermatologiaProfessionalService (camada 8.11): create+audit, toggle, delete em uso → 409.
 */
class DermatologiaProfessionalServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DermatologiaProfessionalService service;

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d2000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma Teste", "derma-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@derma.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita dermatologia_professional_created")
    void create_persistsAndAudits() {
        DermatologiaProfessional p = service.create(COMPANY, USER, "Carla", "Dermatologia clínica", "CRM 12345 RQE 678", null);
        assertThat(p.name()).isEqualTo("Carla");
        assertThat(p.specialty()).isEqualTo("Dermatologia clínica");
        assertThat(p.crmRqe()).isEqualTo("CRM 12345 RQE 678");
        assertThat(p.active()).isTrue();
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'dermatologia_professional_created' and entity_id = ?",
            Long.class, p.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        DermatologiaProfessional p = service.create(COMPANY, USER, "Patrícia", "Dermatologia estética", null, null);
        DermatologiaProfessional off = service.toggle(COMPANY, USER, p.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de profissional com consulta → ProfessionalInUseException (409)")
    void delete_inUse() {
        DermatologiaProfessional p = service.create(COMPANY, USER, "Júlia", "Dermatologia clínica", null, null);
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990170", "Cliente");
        UUID patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Cliente')",
            patientId, COMPANY, contactId);
        UUID typeId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) values (?, ?, 'Consulta', 30)",
            typeId, COMPANY);
        Instant start = Instant.parse("2026-07-01T14:00:00Z");
        jdbcTemplate.update(
            "insert into dermatologia_appointments (company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Cliente', 'Júlia', 'Consulta', 30, ?, ?, 'agendada')",
            COMPANY, p.id(), patientId, typeId, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, p.id()))
            .isInstanceOf(ProfessionalInUseException.class);
    }
}
