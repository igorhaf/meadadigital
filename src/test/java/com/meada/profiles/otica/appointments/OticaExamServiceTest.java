package com.meada.profiles.otica.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.otica.appointments.OticaExamService.ConflictException;
import com.meada.profiles.otica.appointments.OticaExamService.InvalidStatusTransitionException;
import com.meada.profiles.otica.appointments.OticaExamService.OutsideHoursException;
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
 * Testa o OticaExamService (camada 8.12, FLUXO A KEY): schedule OK (end_at = start + duração do
 * config); conflito POR PROFISSIONAL → 409 conflict_slot; MESMO horário com profissional DIFERENTE →
 * OK (paralelismo); fora da janela → rejeitado; transições de status (confirma notifica, realizado
 * silencioso); transição inválida → 409. EvolutionSender fake.
 */
@Import(OticaExamServiceTest.TestConfig.class)
class OticaExamServiceTest extends AbstractIntegrationTest {

    @Autowired
    private OticaExamService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("ca120000-0000-0000-0000-000000000003");
    private UUID profA;
    private UUID profB;
    private UUID contactId;
    private UUID conversationId;

    // 2026-07-01T18:00Z = 15:00 BRT → dentro da janela default 09:00–18:00; exame dura 30min.
    private static final Instant START = Instant.parse("2026-07-01T18:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'otica')",
            COMPANY, "Ótica Exam", "otica-exam");
        profA = UUID.randomUUID();
        profB = UUID.randomUUID();
        jdbcTemplate.update("insert into otica_professionals (id, company_id, name) values (?, ?, 'Dra. A')", profA, COMPANY);
        jdbcTemplate.update("insert into otica_professionals (id, company_id, name) values (?, ?, 'Dr. B')", profB, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990014", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private OticaExamAppointment schedule(UUID prof) {
        return service.create(COMPANY, prof, conversationId, contactId, "Cliente", START, null);
    }

    @Test
    @DisplayName("schedule válido → agendado, end_at = start + 30min (snapshot do config)")
    void schedule_ok() {
        OticaExamAppointment a = schedule(profA);
        assertThat(a.status()).isEqualTo("agendado");
        assertThat(a.durationMinutes()).isEqualTo(30);
        assertThat(a.endAt()).isEqualTo(a.startAt().plusSeconds(1800));
        assertThat(a.professionalName()).isEqualTo("Dra. A");
        assertThat(a.customerName()).isEqualTo("Cliente");
    }

    @Test
    @DisplayName("conflito por profissional (mesmo prof, mesmo horário) → ConflictException (409)")
    void conflict_sameProfessional() {
        schedule(profA);
        assertThatThrownBy(() -> service.create(COMPANY, profA, null, contactId, "Outro", START, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("half-open: exame que COMEÇA exatamente onde o outro TERMINA não conflita; parcial conflita")
    void create_halfOpenWindow() {
        schedule(profA);   // 15:00–15:30 BRT (duração 30 do config)
        // Borda exata (15:30 = fim do primeiro): janela half-open NÃO conflita — invariante do chassi A.
        OticaExamAppointment adjacent = service.create(COMPANY, profA, null, contactId, "Outro",
            START.plusSeconds(30 * 60), null);
        assertThat(adjacent.status()).isEqualTo("agendado");
        // Sobreposição parcial (15:15) → conflita.
        assertThatThrownBy(() -> service.create(COMPANY, profA, null, contactId, "Terceiro",
            START.plusSeconds(15 * 60), null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO horário, profissional DIFERENTE → OK (paralelismo)")
    void sameTime_differentProfessional_ok() {
        schedule(profA);
        OticaExamAppointment b = service.create(COMPANY, profB, null, contactId, "Outro", START, null);
        assertThat(b.status()).isEqualTo("agendado");
        assertThat(b.professionalName()).isEqualTo("Dr. B");
    }

    @Test
    @DisplayName("fora do horário (06:00 BRT) → OutsideHoursException (400)")
    void outsideHours() {
        Instant early = Instant.parse("2026-07-01T09:00:00Z");   // 06:00 BRT, antes de 09:00.
        assertThatThrownBy(() -> service.create(COMPANY, profA, null, contactId, "Cliente", early, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("updateStatus agendado→confirmado → notifica o cliente")
    void confirm_notifies() {
        OticaExamAppointment a = schedule(profA);
        OticaExamAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmado");
    }

    @Test
    @DisplayName("updateStatus confirmado→realizado → silencioso")
    void realizado_silent() {
        OticaExamAppointment a = schedule(profA);
        service.updateStatus(COMPANY, a.id(), "confirmado");
        fakeEvolution.reset();
        OticaExamAppointment done = service.updateStatus(COMPANY, a.id(), "realizado");
        assertThat(done.status()).isEqualTo("realizado");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("transição inválida (agendado→realizado) → InvalidStatusTransitionException (409)")
    void invalidTransition() {
        OticaExamAppointment a = schedule(profA);
        assertThatThrownBy(() -> service.updateStatus(COMPANY, a.id(), "realizado"))
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
            return "key-otica-exam";
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
