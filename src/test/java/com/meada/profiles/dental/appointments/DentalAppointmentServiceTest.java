package com.meada.profiles.dental.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.dental.appointments.DentalAppointmentService.ConflictException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.dental.appointments.DentalAppointmentService.OutsideHoursException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o DentalAppointmentService (camada 7.4): create válida → agendada, fora do horário, conflito,
 * transição com notificação (confirmada) e transição silenciosa (realizada). EvolutionSender fake.
 */
@Import(DentalAppointmentServiceTest.TestConfig.class)
class DentalAppointmentServiceTest extends AbstractIntegrationTest {

    @Autowired
    private DentalAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c9000000-0000-0000-0000-000000000002");
    private UUID patientId;
    private UUID conversationId;

    // 2026-07-01T15:00-03:00 (BRT) → dentro da janela 08:00–18:00; dura 30min.
    private static final Instant START = Instant.parse("2026-07-01T18:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'dental')",
            COMPANY, "Clínica O", "clinica-o");
        patientId = UUID.randomUUID();
        jdbcTemplate.update("insert into dental_patients (id, company_id, name) values (?, ?, 'Maria Souza')",
            patientId, COMPANY);
        UUID instance = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990030", "Maria");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private DentalAppointment seedAppointment() {
        return service.create(COMPANY, patientId, conversationId, START, "Limpeza", null);
    }

    @Test
    @DisplayName("create válida → status agendada")
    void create_agendada() {
        DentalAppointment a = seedAppointment();
        assertThat(a.status()).isEqualTo("agendada");
        assertThat(a.patientName()).isEqualTo("Maria Souza");
        assertThat(a.type()).isEqualTo("Limpeza");
    }

    @Test
    @DisplayName("create fora do horário (06:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T06:00-03:00 → 09:00 UTC = 06:00 BRT; antes de opens_at 08:00 BRT.
        Instant early = Instant.parse("2026-07-01T09:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, patientId, null, early, "Avaliação", null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com conflito (mesmo horário, mesmo company) → ConflictException (409)")
    void create_conflict() {
        seedAppointment();   // 15:00–15:30 BRT
        // mesmo horário, outro paciente → conflito (1 dentista por tenant).
        UUID other = UUID.randomUUID();
        jdbcTemplate.update("insert into dental_patients (id, company_id, name) values (?, ?, 'Outro')",
            other, COMPANY);
        assertThatThrownBy(() -> service.create(COMPANY, other, null, START, "Canal", null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("half-open: consulta que COMEÇA exatamente onde a outra TERMINA não conflita; sobreposição parcial conflita")
    void create_halfOpenWindow() {
        seedAppointment();   // 15:00–15:30 BRT
        UUID other = UUID.randomUUID();
        jdbcTemplate.update("insert into dental_patients (id, company_id, name) values (?, ?, 'Outro')",
            other, COMPANY);
        // Borda exata (15:30 = fim da primeira): janela half-open NOT (end <= :s OR start >= :e)
        // NÃO pode acusar conflito — invariante do chassi A.
        DentalAppointment adjacent = service.create(COMPANY, other, null,
            START.plusSeconds(30 * 60), "Canal", null);
        assertThat(adjacent.status()).isEqualTo("agendada");

        // Sobreposição parcial (15:15–15:45) → conflita.
        UUID third = UUID.randomUUID();
        jdbcTemplate.update("insert into dental_patients (id, company_id, name) values (?, ?, 'Terceiro')",
            third, COMPANY);
        assertThatThrownBy(() -> service.create(COMPANY, third, null,
            START.plusSeconds(15 * 60), "Avaliação", null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("updateStatus agendada→confirmada → notifica o paciente")
    void confirm_notifies() {
        DentalAppointment a = seedAppointment();
        DentalAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmada");
        assertThat(confirmed.status()).isEqualTo("confirmada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmada");
    }

    @Test
    @DisplayName("updateStatus confirmada→realizada → silencioso (sem notificação)")
    void realizada_silent() {
        DentalAppointment a = seedAppointment();
        service.updateStatus(COMPANY, a.id(), "confirmada");
        fakeEvolution.reset();
        DentalAppointment done = service.updateStatus(COMPANY, a.id(), "realizada");
        assertThat(done.status()).isEqualTo("realizada");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("transição inválida (agendada→realizada) → InvalidStatusTransitionException (409)")
    void invalidTransition() {
        DentalAppointment a = seedAppointment();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, a.id(), "realizada"))
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
            return "key-dental";
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
