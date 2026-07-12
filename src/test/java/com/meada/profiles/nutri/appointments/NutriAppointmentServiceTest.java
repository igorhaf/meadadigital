package com.meada.profiles.nutri.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.ConflictException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InactiveProfessionalException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.InvalidTypeException;
import com.meada.profiles.nutri.appointments.NutriAppointmentService.OutsideHoursException;
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
 * Testa o NutriAppointmentService (camada 8.0): create válida (snapshots paciente/profissional, status
 * agendado, tipo), fora do horário, profissional inativo, tipo inválido, conflito MESMO PROFISSIONAL,
 * MESMO HORÁRIO PROFISSIONAL DIFERENTE = OK (paralelismo), confirmação com notificação. EvolutionSender
 * fake.
 */
@Import(NutriAppointmentServiceTest.TestConfig.class)
class NutriAppointmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private NutriAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cd000000-0000-0000-0000-000000000004");
    private UUID profCarla;
    private UUID profPatricia;
    private UUID profInativa;
    private UUID contactId;
    private UUID conversationId;
    private UUID patientMarina;

    // 2026-07-01T11:00-03:00 (BRT) → dentro de 08:00–18:00; duração default 60min.
    private static final Instant START = Instant.parse("2026-07-01T14:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')",
            COMPANY, "Nutri S", "nutri-s");
        profCarla = UUID.randomUUID();
        profPatricia = UUID.randomUUID();
        profInativa = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name, specialty) values (?, ?, 'Carla', 'Nutrição clínica')",
            profCarla, COMPANY);
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name, specialty) values (?, ?, 'Patrícia', 'Nutrição esportiva')",
            profPatricia, COMPANY);
        jdbcTemplate.update("insert into nutri_professionals (id, company_id, name, active) values (?, ?, 'Inativa', false)",
            profInativa, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990090", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        patientMarina = UUID.randomUUID();
        jdbcTemplate.update("insert into nutri_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientMarina, COMPANY, contactId);
    }

    private NutriAppointment seedWithCarla() {
        return service.create(COMPANY, profCarla, patientMarina, conversationId, "primeira", START, null, null);
    }

    @Test
    @DisplayName("create válida → agendado, com snapshots de profissional/paciente e tipo")
    void create_agendado() {
        NutriAppointment a = seedWithCarla();
        assertThat(a.status()).isEqualTo("agendado");
        assertThat(a.professionalName()).isEqualTo("Carla");
        assertThat(a.patientName()).isEqualTo("Marina");
        assertThat(a.appointmentType()).isEqualTo("primeira");
        assertThat(a.durationMinutes()).isEqualTo(60);
    }

    @Test
    @DisplayName("create fora do horário (07:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T07:00-03:00 → 10:00 UTC = 07:00 BRT; antes de opens_at 08:00 BRT.
        Instant early = Instant.parse("2026-07-01T10:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, null, "primeira", early, null, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com profissional inativo → InactiveProfessionalException (400)")
    void create_inactiveProfessional() {
        assertThatThrownBy(() -> service.create(COMPANY, profInativa, patientMarina, null, "primeira", START, null, null))
            .isInstanceOf(InactiveProfessionalException.class);
    }

    @Test
    @DisplayName("create com tipo inválido ('foo') → InvalidTypeException (400)")
    void create_invalidType() {
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, null, "foo", START, null, null))
            .isInstanceOf(InvalidTypeException.class);
    }

    @Test
    @DisplayName("conflito MESMO PROFISSIONAL (Carla, mesmo horário) → ConflictException (409)")
    void create_conflictSameProfessional() {
        seedWithCarla();   // Carla 11:00–12:00 BRT
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, null, "retorno", START, null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("half-open: consulta que COMEÇA exatamente onde a outra TERMINA não conflita; parcial conflita")
    void create_halfOpenWindow() {
        seedWithCarla();   // Carla 11:00–12:00 BRT
        // Borda exata (12:00 = fim da primeira): janela half-open NÃO conflita — invariante do chassi A.
        NutriAppointment adjacent = service.create(COMPANY, profCarla, patientMarina, null, "retorno",
            START.plusSeconds(60 * 60), null, null);
        assertThat(adjacent.status()).isEqualTo("agendado");
        // Sobreposição parcial (11:30) → conflita.
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, null, "retorno",
            START.plusSeconds(30 * 60), null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO HORÁRIO, PROFISSIONAL DIFERENTE (Patrícia) → OK (paralelismo)")
    void create_sameSlotDifferentProfessional() {
        seedWithCarla();   // Carla 11:00 BRT
        assertThatCode(() -> service.create(COMPANY, profPatricia, patientMarina, null, "retorno", START, null, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from nutri_appointments where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateStatus agendado→confirmado → notifica com 'Consulta confirmada'")
    void confirm_notifies() {
        NutriAppointment a = seedWithCarla();
        NutriAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Consulta confirmada").contains("às");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-nutri";
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
