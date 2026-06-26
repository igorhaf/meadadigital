package com.meada.whatsapp.profiles.dermatologia.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AgendamentoDermaConfirmHandler (camada 8.11): parse OK + create nos 2 MODOS — patient_id
 * existente E new_patient (cadastra paciente + agenda) —, professional inválido → empty, conflito →
 * empty, sem tag → empty.
 */
class AgendamentoDermaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AgendamentoDermaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;
    private UUID profId;
    private UUID typeId;
    private UUID patientId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma H", "derma-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990195", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name) values (?, ?, 'Carla')", profId, COMPANY);
        typeId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) values (?, ?, 'Consulta', 30)",
            typeId, COMPANY);
        patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientId, COMPANY, contactId);
    }

    @Test
    @DisplayName("MODO patient_id existente → cria agendada para o paciente informado")
    void parseAndCreate_existingPatient() {
        String aiText = "Perfeito, Marina! Agendei sua consulta com a Carla pra 01/07 às 11h.\n"
            + "<consulta_derma>{\"professional_id\":\"" + profId + "\",\"procedure_type_id\":\"" + typeId
            + "\",\"patient_id\":\"" + patientId
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\",\"notes\":\"\"}</consulta_derma>";

        Optional<DermatologiaAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendada");
        assertThat(a.get().professionalName()).isEqualTo("Carla");
        assertThat(a.get().patientName()).isEqualTo("Marina");
        assertThat(a.get().procedureTypeName()).isEqualTo("Consulta");
    }

    @Test
    @DisplayName("MODO new_patient → cadastra o paciente E agenda (count de pacientes sobe)")
    void parseAndCreate_newPatient() {
        Long before = jdbcTemplate.queryForObject("select count(*) from dermatologia_patients where company_id = ?",
            Long.class, COMPANY);

        String aiText = "Cadastrei o Bruno e já agendei!\n"
            + "<consulta_derma>{\"professional_id\":\"" + profId + "\",\"procedure_type_id\":\"" + typeId
            + "\",\"new_patient\":{\"name\":\"Bruno\",\"birth_date\":\"1985-03-10\"},"
            + "\"date\":\"2026-07-01\",\"start_time\":\"12:00\"}</consulta_derma>";

        Optional<DermatologiaAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().patientName()).isEqualTo("Bruno");
        Long after = jdbcTemplate.queryForObject("select count(*) from dermatologia_patients where company_id = ?",
            Long.class, COMPANY);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("professional_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidProfessional() {
        String aiText = "Agendado!\n<consulta_derma>{\"professional_id\":\"" + UUID.randomUUID()
            + "\",\"procedure_type_id\":\"" + typeId + "\",\"patient_id\":\"" + patientId + "\","
            + "\"date\":\"2026-07-01\",\"start_time\":\"11:00\"}</consulta_derma>";
        Optional<DermatologiaAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from dermatologia_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("conflito no slot do profissional → Optional.empty (não criado)")
    void parseAndCreate_conflict() {
        UUID otherPatient = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Outro')",
            otherPatient, COMPANY, contactId);
        java.time.Instant start = java.time.Instant.parse("2026-07-01T14:00:00Z"); // 11:00 BRT
        jdbcTemplate.update(
            "insert into dermatologia_appointments (company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, 'Outro', 'Carla', 'Consulta', 30, ?, ?, 'agendada')",
            COMPANY, profId, otherPatient, typeId, contactId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(start.plusSeconds(1800)));

        String aiText = "Agendado!\n<consulta_derma>{\"professional_id\":\"" + profId + "\",\"procedure_type_id\":\"" + typeId
            + "\",\"patient_id\":\"" + patientId + "\",\"date\":\"2026-07-01\",\"start_time\":\"11:00\"}</consulta_derma>";
        Optional<DermatologiaAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<DermatologiaAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer marcar uma consulta dermatológica?");
        assertThat(a).isEmpty();
    }
}
