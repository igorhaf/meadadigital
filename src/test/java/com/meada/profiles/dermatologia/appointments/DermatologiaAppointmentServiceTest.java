package com.meada.profiles.dermatologia.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.dermatologia.appointments.DermatologiaAppointmentService.ConflictException;
import com.meada.profiles.dermatologia.appointments.DermatologiaAppointmentService.InactiveProfessionalException;
import com.meada.profiles.dermatologia.appointments.DermatologiaAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.dermatologia.appointments.DermatologiaAppointmentService.OutsideHoursException;
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
 * Testa o DermatologiaAppointmentService (camada 8.11): create válida (snapshots paciente/profissional/
 * TIPO, duração do tipo, end_at materializado, status agendada), fora do horário, profissional inativo,
 * conflito MESMO PROFISSIONAL, MESMO HORÁRIO PROFISSIONAL DIFERENTE = OK (paralelismo), confirmação com
 * notificação, transição inválida. EvolutionSender fake.
 */
@Import(DermatologiaAppointmentServiceTest.TestConfig.class)
class DermatologiaAppointmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DermatologiaAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("d1000000-0000-0000-0000-000000000004");
    private UUID profCarla;
    private UUID profPatricia;
    private UUID profInativa;
    private UUID typeConsulta;   // 30 min
    private UUID contactId;
    private UUID conversationId;
    private UUID patientMarina;

    // 2026-07-01T11:00-03:00 (BRT) → dentro de 08:00–18:00.
    private static final Instant START = Instant.parse("2026-07-01T14:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dermatologia')",
            COMPANY, "Derma S", "derma-s");
        profCarla = UUID.randomUUID();
        profPatricia = UUID.randomUUID();
        profInativa = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name, specialty) values (?, ?, 'Carla', 'Clínica')",
            profCarla, COMPANY);
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name, specialty) values (?, ?, 'Patrícia', 'Estética')",
            profPatricia, COMPANY);
        jdbcTemplate.update("insert into dermatologia_professionals (id, company_id, name, active) values (?, ?, 'Inativa', false)",
            profInativa, COMPANY);
        typeConsulta = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_procedure_types (id, company_id, name, duration_minutes) values (?, ?, 'Consulta', 30)",
            typeConsulta, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990190", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        patientMarina = UUID.randomUUID();
        jdbcTemplate.update("insert into dermatologia_patients (id, company_id, contact_id, name) values (?, ?, ?, 'Marina')",
            patientMarina, COMPANY, contactId);
    }

    private DermatologiaAppointment seedWithCarla() {
        return service.create(COMPANY, profCarla, patientMarina, typeConsulta, conversationId, START, null);
    }

    @Test
    @DisplayName("create válida → agendada, snapshots profissional/paciente/tipo, duração do tipo, end_at materializado")
    void create_agendada() {
        DermatologiaAppointment a = seedWithCarla();
        assertThat(a.status()).isEqualTo("agendada");
        assertThat(a.professionalName()).isEqualTo("Carla");
        assertThat(a.patientName()).isEqualTo("Marina");
        assertThat(a.procedureTypeName()).isEqualTo("Consulta");
        assertThat(a.durationMinutes()).isEqualTo(30);
        // end_at MATERIALIZADO = start + 30 min.
        assertThat(a.endAt()).isEqualTo(a.startAt().plusSeconds(30 * 60L));
        assertThat(a.endAt()).isEqualTo(START.plusSeconds(30 * 60L));
    }

    @Test
    @DisplayName("create fora do horário (07:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T07:00-03:00 → 10:00 UTC = 07:00 BRT; antes de opens_at 08:00 BRT.
        Instant early = Instant.parse("2026-07-01T10:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, typeConsulta, null, early, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com profissional inativo → InactiveProfessionalException (400)")
    void create_inactiveProfessional() {
        assertThatThrownBy(() -> service.create(COMPANY, profInativa, patientMarina, typeConsulta, null, START, null))
            .isInstanceOf(InactiveProfessionalException.class);
    }

    @Test
    @DisplayName("conflito MESMO PROFISSIONAL (Carla, mesmo horário) → ConflictException (409)")
    void create_conflictSameProfessional() {
        seedWithCarla();   // Carla 11:00–11:30 BRT
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, typeConsulta, null, START, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("half-open: consulta que COMEÇA exatamente onde a outra TERMINA não conflita; parcial conflita")
    void create_halfOpenWindow() {
        seedWithCarla();   // Carla 11:00–11:30 BRT
        // Borda exata (11:30 = fim da primeira): janela half-open NÃO conflita — invariante do chassi A.
        DermatologiaAppointment adjacent = service.create(COMPANY, profCarla, patientMarina, typeConsulta,
            null, START.plusSeconds(30 * 60), null);
        assertThat(adjacent.status()).isEqualTo("agendada");
        // Sobreposição parcial (11:15) → conflita.
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, patientMarina, typeConsulta,
            null, START.plusSeconds(15 * 60), null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO HORÁRIO, PROFISSIONAL DIFERENTE (Patrícia) → OK (paralelismo)")
    void create_sameSlotDifferentProfessional() {
        seedWithCarla();   // Carla 11:00 BRT
        assertThatCode(() -> service.create(COMPANY, profPatricia, patientMarina, typeConsulta, null, START, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from dermatologia_appointments where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateStatus agendada→confirmada → notifica com 'Consulta confirmada'")
    void confirm_notifies() {
        DermatologiaAppointment a = seedWithCarla();
        DermatologiaAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmada");
        assertThat(confirmed.status()).isEqualTo("confirmada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Consulta confirmada").contains("às");
    }

    @Test
    @DisplayName("updateStatus agendada→confirmada→cancelada notifica; confirmada→agendada inválida → 409")
    void invalidTransition() {
        DermatologiaAppointment a = seedWithCarla();
        service.updateStatus(COMPANY, a.id(), "confirmada");
        // cancelada notifica
        DermatologiaAppointment cancelled = service.updateStatus(COMPANY, a.id(), "cancelada");
        assertThat(cancelled.status()).isEqualTo("cancelada");
        // confirmada → agendada (de uma cancelada, terminal) é inválida.
        assertThatThrownBy(() -> service.updateStatus(COMPANY, a.id(), "agendada"))
            .isInstanceOf(InvalidStatusTransitionException.class);
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
