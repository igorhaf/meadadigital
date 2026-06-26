package com.meada.whatsapp.profiles.dermatologia.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o EntregaPreparoHandler (camada 8.11): ENTREGA READ-ONLY da nota de preparo do tipo da
 * consulta (texto VERBATIM). Cobre a entrega bem-sucedida (texto exato + envio), a BARREIRA DE
 * SEGURANÇA (não entregar preparo de consulta de OUTRO contato), tipo SEM preparo → empty, e sem tag
 * → empty. EvolutionSender fake.
 */
@Import(EntregaPreparoHandlerTest.TestConfig.class)
class EntregaPreparoHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private EntregaPreparoHandler handler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000006");
    private static final String PREP_BOTOX = "PREPARO BOTOX: suspender ácido 5 dias antes; comparecer sem maquiagem.";

    private UUID contactA;            // Marina
    private UUID conversationA;
    private UUID contactB;            // Pedro
    private UUID apptWithPrepA;       // consulta do contato A, tipo COM preparo
    private UUID apptWithPrepB;       // consulta do contato B, tipo COM preparo
    private UUID apptNoPrepA;         // consulta do contato A, tipo SEM preparo

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma E", "derma-e");

        UUID prof = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name) values (?, ?, 'Carla')", prof, COMPANY);

        UUID typeWithPrep = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes, prep_instructions) "
            + "values (?, ?, 'Botox', 60, ?)", typeWithPrep, COMPANY, PREP_BOTOX);
        UUID typeNoPrep = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) "
            + "values (?, ?, 'Consulta', 30)", typeNoPrep, COMPANY);

        UUID instanceA = UUID.randomUUID();
        contactA = UUID.randomUUID();
        conversationA = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceA, COMPANY, "inst-a", "tok-a");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactA, COMPANY, "+5511999990200", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationA, COMPANY, contactA, instanceA);

        UUID instanceB = UUID.randomUUID();
        contactB = UUID.randomUUID();
        UUID conversationB = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceB, COMPANY, "inst-b", "tok-b");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactB, COMPANY, "+5511999990201", "Pedro");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationB, COMPANY, contactB, instanceB);

        UUID patientA = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientA, COMPANY, contactA);
        UUID patientB = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Pedro')",
            patientB, COMPANY, contactB);

        apptWithPrepA = seedAppointment(prof, patientA, typeWithPrep, "Marina", "Botox", contactA);
        apptWithPrepB = seedAppointment(prof, patientB, typeWithPrep, "Pedro", "Botox", contactB);
        apptNoPrepA = seedAppointment(prof, patientA, typeNoPrep, "Marina", "Consulta", contactA);
    }

    private UUID seedAppointment(UUID prof, UUID patient, UUID type, String patientName, String typeName, UUID contactId) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-07-10T14:00:00Z");
        jdbcTemplate.update(
            "insert into dermatologia_appointments (id, company_id, professional_id, patient_id, procedure_type_id, contact_id, "
                + "patient_name, professional_name, procedure_type_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'Carla', ?, 30, ?, ?, 'agendada')",
            id, COMPANY, prof, patient, type, contactId, patientName, typeName,
            java.sql.Timestamp.from(start), java.sql.Timestamp.from(start.plusSeconds(1800)));
        return id;
    }

    @Test
    @DisplayName("entrega o preparo da consulta do próprio contato → devolve o texto EXATO + envia esse texto")
    void deliver_ownAppointment() {
        String aiText = "Aqui estão suas orientações:\n<entrega_preparo>{\"appointment_id\":\"" + apptWithPrepA + "\"}</entrega_preparo>";

        Optional<String> delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).contains(PREP_BOTOX);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).isEqualTo(PREP_BOTOX);
    }

    @Test
    @DisplayName("consulta de tipo SEM preparo → Optional.empty (nada enviado)")
    void deliver_noPrep() {
        String aiText = "Vou ver o preparo...\n<entrega_preparo>{\"appointment_id\":\"" + apptNoPrepA + "\"}</entrega_preparo>";

        Optional<String> delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isEmpty();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("BARREIRA: preparo de consulta de OUTRO contato → Optional.empty (não vaza)")
    void deliver_securityBarrier() {
        // appointment de Pedro (contactB), mas a conversa/contactId é da Marina (contactA).
        String aiText = "Aqui está o preparo:\n<entrega_preparo>{\"appointment_id\":\"" + apptWithPrepB + "\"}</entrega_preparo>";

        Optional<String> delivered = handler.parseAndDeliver(COMPANY, conversationA, contactA, aiText);

        assertThat(delivered).isEmpty();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty")
    void deliver_noTag() {
        Optional<String> delivered = handler.parseAndDeliver(
            COMPANY, conversationA, contactA, "Oi! Quer que eu te mande as orientações de preparo?");
        assertThat(delivered).isEmpty();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-derma";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
