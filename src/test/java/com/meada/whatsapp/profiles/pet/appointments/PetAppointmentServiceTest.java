package com.meada.whatsapp.profiles.pet.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.pet.appointments.PetAppointmentService.ConflictException;
import com.meada.whatsapp.profiles.pet.appointments.PetAppointmentService.InactiveProfessionalException;
import com.meada.whatsapp.profiles.pet.appointments.PetAppointmentService.OutsideHoursException;
import com.meada.whatsapp.profiles.pet.appointments.PetAppointmentService.SpeciesMismatchException;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PetAppointmentService (camada 7.8): create válida (snapshots, incl. tutorName do contato do
 * animal), fora do horário, profissional inativo, SPECIES MISMATCH, conflito MESMO PROFISSIONAL,
 * MESMO HORÁRIO PROFISSIONAL DIFERENTE = OK (chave da SM), e confirmação com notificação (animal).
 * EvolutionSender fake.
 */
@Import(PetAppointmentServiceTest.TestConfig.class)
class PetAppointmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PetAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000004");
    private UUID profCarla;
    private UUID profPatricia;
    private UUID serviceBanho;
    private UUID contactId;
    private UUID conversationId;
    private UUID animalRex;

    // 2026-07-01T12:00-03:00 (BRT) → dentro de 09:00–19:00; Banho dura 45min.
    private static final Instant START = Instant.parse("2026-07-01T15:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pet')",
            COMPANY, "Pet S", "pet-s");
        profCarla = UUID.randomUUID();
        profPatricia = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name, specialty) values (?, ?, 'Carla', 'Veterinária')",
            profCarla, COMPANY);
        jdbcTemplate.update("insert into pet_professionals (id, company_id, name, specialty) values (?, ?, 'Patrícia', 'Tosadora')",
            profPatricia, COMPANY);
        serviceBanho = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, category, duration_minutes, price_cents) "
            + "values (?, ?, 'Banho', 'Higiene', 45, 8000)", serviceBanho, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990090", "Joana");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        animalRex = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Rex', 'cao')",
            animalRex, COMPANY, contactId);
    }

    private PetAppointment seedWithCarla() {
        return service.create(COMPANY, profCarla, serviceBanho, animalRex, conversationId, START, null);
    }

    @Test
    @DisplayName("create válida → agendado, com snapshots de profissional/serviço/animal/preço/tutor")
    void create_agendado() {
        PetAppointment a = seedWithCarla();
        assertThat(a.status()).isEqualTo("agendado");
        assertThat(a.professionalName()).isEqualTo("Carla");
        assertThat(a.serviceName()).isEqualTo("Banho");
        assertThat(a.animalName()).isEqualTo("Rex");
        assertThat(a.priceCents()).isEqualTo(8000);
        assertThat(a.durationMinutes()).isEqualTo(45);
        assertThat(a.tutorName()).isEqualTo("Joana");
    }

    @Test
    @DisplayName("create fora do horário (07:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T07:00-03:00 → 10:00 UTC = 07:00 BRT; antes de opens_at 09:00 BRT.
        Instant early = Instant.parse("2026-07-01T10:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, serviceBanho, animalRex, null, early, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com profissional inativo → InactiveProfessionalException (400)")
    void create_inactiveProfessional() {
        jdbcTemplate.update("update pet_professionals set active = false where id = ?", profCarla);
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, serviceBanho, animalRex, null, START, null))
            .isInstanceOf(InactiveProfessionalException.class);
    }

    @Test
    @DisplayName("SPECIES MISMATCH (serviço só gato, animal cao) → SpeciesMismatchException (400)")
    void create_speciesMismatch() {
        UUID svcGato = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_services (id, company_id, name, category, duration_minutes, price_cents, species_restriction) "
            + "values (?, ?, 'Tosa felina', 'Estética', 45, 7000, 'gato')", svcGato, COMPANY);
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, svcGato, animalRex, null, START, null))
            .isInstanceOf(SpeciesMismatchException.class);
    }

    @Test
    @DisplayName("conflito MESMO PROFISSIONAL (Carla, mesmo horário) → ConflictException (409)")
    void create_conflictSameProfessional() {
        seedWithCarla();   // Carla 12:00–12:45 BRT
        UUID animal2 = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Bidu', 'cao')",
            animal2, COMPANY, contactId);
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, serviceBanho, animal2, null, START, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO HORÁRIO, PROFISSIONAL DIFERENTE (Patrícia) → OK (paralelismo, chave da SM)")
    void create_sameSlotDifferentProfessional() {
        seedWithCarla();   // Carla 12:00 BRT
        UUID animal2 = UUID.randomUUID();
        jdbcTemplate.update("insert into pet_animals (id, company_id, contact_id, name, species) values (?, ?, ?, 'Bidu', 'cao')",
            animal2, COMPANY, contactId);
        // Patrícia no MESMO horário não conflita (conflito é por profissional).
        assertThatCode(() -> service.create(COMPANY, profPatricia, serviceBanho, animal2, null, START, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from pet_appointments where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);   // ambos criados no mesmo horário.
    }

    @Test
    @DisplayName("updateStatus agendado→confirmado → notifica com nome do animal")
    void confirm_notifies() {
        PetAppointment a = seedWithCarla();
        PetAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmado").contains("Rex");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-pet";
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
