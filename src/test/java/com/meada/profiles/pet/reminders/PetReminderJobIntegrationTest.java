package com.meada.profiles.pet.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.pet.appointments.ConfirmacaoPetHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Pet 1 (backlog #1): lembrete de véspera via {@link PetReminderJob} e o
 * loop de confirmação via {@link ConfirmacaoPetHandler} (tag {@code <confirmacao_pet>}, barreira
 * de contato do tutor). EvolutionSender é um FAKE.
 */
@Import(PetReminderJobIntegrationTest.TestConfig.class)
class PetReminderJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("ad000000-0000-0000-0000-0000000000b2");
    private static final UUID INSTANCE = UUID.fromString("ad100000-0000-0000-0000-0000000000b2");
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private PetReminderJob job;
    @Autowired
    private ConfirmacaoPetHandler confirmacaoHandler;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID professionalId;
    private UUID serviceId;
    private UUID animalId;
    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pet')",
            COMPANY, "Pet Reminder", "pet-reminder");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-pet", "tok-pet");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Tutor Téo')",
            contactId, COMPANY, "+5511999990241");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
        professionalId = jdbcTemplate.queryForObject(
            "insert into pet_professionals (company_id, name) values (?, 'Duda') returning id",
            UUID.class, COMPANY);
        serviceId = jdbcTemplate.queryForObject(
            "insert into pet_services (company_id, name, category, duration_minutes) "
                + "values (?, 'Banho', 'banho_tosa', 60) returning id",
            UUID.class, COMPANY);
        animalId = jdbcTemplate.queryForObject(
            "insert into pet_animals (company_id, contact_id, name, species) "
                + "values (?, ?, 'Thor', 'cao') returning id",
            UUID.class, COMPANY, contactId);
    }

    private UUID seedAppointment(String status, Instant startAt, UUID conversation, UUID contact) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into pet_appointments (id, company_id, professional_id, service_id, animal_id, "
                + "contact_id, conversation_id, tutor_name, animal_name, animal_species, "
                + "professional_name, service_name, duration_minutes, start_at, end_at, status) "
                + "values (?, ?, ?, ?, ?, ?, ?, 'Tutor Téo', 'Thor', 'cao', 'Duda', 'Banho', 60, ?, ?, ?)",
            id, COMPANY, professionalId, serviceId, animalId, contact, conversation,
            java.sql.Timestamp.from(startAt),
            java.sql.Timestamp.from(startAt.plus(60, ChronoUnit.MINUTES)), status);
        return id;
    }

    private Instant tomorrowAt(int hour) {
        return LocalDate.now(SP).plusDays(1).atTime(LocalTime.of(hour, 0)).atZone(SP).toInstant();
    }

    @Test
    @DisplayName("agendado de amanhã → lembrete com nome do pet 1x + idempotente; toggle off → nada")
    void reminder_onceAndToggle() {
        seedAppointment("agendado", tomorrowAt(14), conversationId, contactId);

        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Thor").contains("Banho").contains("Duda");

        fakeEvolution.reset();
        assertThat(job.runReminders()).isZero();

        jdbcTemplate.update(
            "insert into pet_config (company_id, opens_at, closes_at, reminder_enabled) "
                + "values (?, '09:00', '19:00', false)", COMPANY);
        seedAppointment("confirmado", tomorrowAt(16), conversationId, contactId);
        assertThat(job.runReminders()).isZero();
    }

    @Test
    @DisplayName("agendamento manual sem conversa → marca sem envio")
    void noChannel_markedWithoutSend() {
        UUID a = seedAppointment("agendado", tomorrowAt(11), null, null);
        assertThat(job.runReminders()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).isEmpty();
        java.sql.Timestamp marked = jdbcTemplate.queryForObject(
            "select reminded_start_at from pet_appointments where id = ?",
            java.sql.Timestamp.class, a);
        assertThat(marked).isNotNull();
    }

    @Test
    @DisplayName("<confirmacao_pet>: SIM confirma com barreira de contato; desmarcar cancela")
    void confirmacaoTag() {
        UUID a = seedAppointment("agendado", tomorrowAt(14), conversationId, contactId);
        String tag = "<confirmacao_pet>{\"appointment_id\":\"" + a
            + "\",\"decisao\":\"confirmado\"}</confirmacao_pet>";

        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, UUID.randomUUID(), tag))
            .isEmpty();   // barreira de contato.
        assertThat(statusOf(a)).isEqualTo("agendado");

        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, tag)).isPresent();
        assertThat(statusOf(a)).isEqualTo("confirmado");

        String cancel = "<confirmacao_pet>{\"appointment_id\":\"" + a
            + "\",\"decisao\":\"cancelado\"}</confirmacao_pet>";
        assertThat(confirmacaoHandler.parseAndApply(COMPANY, conversationId, contactId, cancel)).isPresent();
        assertThat(statusOf(a)).isEqualTo("cancelado");
    }

    private String statusOf(UUID id) {
        return jdbcTemplate.queryForObject(
            "select status from pet_appointments where id = ?", String.class, id);
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-pet-reminder";
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
