package com.meada.whatsapp.profiles.pet.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AgendamentoPetConfirmHandler (camada 7.8): parse OK + create nos 2 MODOS — animal_id
 * existente E new_animal (cadastra animal + agenda) —, professional inválido → empty, sem tag → empty.
 */
class AgendamentoPetConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private AgendamentoPetConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000005");
    private UUID conversationId;
    private UUID contactId;
    private UUID profId;
    private UUID serviceId;
    private UUID animalId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pet')",
            COMPANY, "Pet H", "pet-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990095", "Joana");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        profId = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name) values (?, ?, 'Carla')", profId, COMPANY);
        serviceId = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, duration_minutes) values (?, ?, 'Banho', 45)",
            serviceId, COMPANY);
        animalId = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Rex', 'cao')",
            animalId, COMPANY, contactId);
    }

    @Test
    @DisplayName("MODO animal_id existente → cria agendado para o animal informado")
    void parseAndCreate_existingAnimal() {
        String aiText = "Perfeito, Joana! Agendei o Banho do Rex com a Carla pra 01/07 às 12h.\n"
            + "<agendamento_pet>{\"professional_id\":\"" + profId + "\",\"service_id\":\"" + serviceId
            + "\",\"animal_id\":\"" + animalId + "\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\",\"notes\":\"\"}</agendamento_pet>";

        Optional<PetAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().status()).isEqualTo("agendado");
        assertThat(a.get().professionalName()).isEqualTo("Carla");
        assertThat(a.get().serviceName()).isEqualTo("Banho");
        assertThat(a.get().animalName()).isEqualTo("Rex");
        assertThat(a.get().tutorName()).isEqualTo("Joana");
    }

    @Test
    @DisplayName("MODO new_animal → cadastra o animal E agenda (count de animais sobe)")
    void parseAndCreate_newAnimal() {
        Long before = jdbcTemplate.queryForObject("select count(*) from pet_animals where company_id = ?",
            Long.class, COMPANY);

        String aiText = "Cadastrei o Bidu e já agendei!\n"
            + "<agendamento_pet>{\"professional_id\":\"" + profId + "\",\"service_id\":\"" + serviceId
            + "\",\"new_animal\":{\"name\":\"Bidu\",\"species\":\"cao\",\"breed\":\"Poodle\"},"
            + "\"date\":\"2026-07-01\",\"start_time\":\"14:00\"}</agendamento_pet>";

        Optional<PetAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().animalName()).isEqualTo("Bidu");
        Long after = jdbcTemplate.queryForObject("select count(*) from pet_animals where company_id = ?",
            Long.class, COMPANY);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("professional_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidProfessional() {
        String aiText = "Agendado!\n<agendamento_pet>{\"professional_id\":\"" + UUID.randomUUID()
            + "\",\"service_id\":\"" + serviceId + "\",\"animal_id\":\"" + animalId
            + "\",\"date\":\"2026-07-01\",\"start_time\":\"12:00\"}</agendamento_pet>";
        Optional<PetAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(a).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pet_appointments", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<PetAppointment> a = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer agendar um horário pro seu pet?");
        assertThat(a).isEmpty();
    }
}
