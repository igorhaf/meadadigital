package com.meada.profiles.fotografia.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.ConflictException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.InactiveProfessionalException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.InvalidStatusTransitionException;
import com.meada.profiles.fotografia.appointments.FotografiaAppointmentService.OutsideHoursException;
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
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o FotografiaAppointmentService (camada 8.16): create válida (snapshots cliente/profissional/
 * PACOTE, duração+delivery_days do pacote, end_at + delivery_due_date materializados, status
 * agendada), fora do horário, profissional inativo, conflito MESMO PROFISSIONAL, MESMO HORÁRIO
 * PROFISSIONAL DIFERENTE = OK (paralelismo), confirmação com notificação, realizada→entregue, transição
 * inválida. EvolutionSender fake. Clone do DermatologiaAppointmentServiceTest.
 */
@Import(FotografiaAppointmentServiceTest.TestConfig.class)
class FotografiaAppointmentServiceTest extends AbstractIntegrationTest {

    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private FotografiaAppointmentService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("f0000000-0000-0000-0000-000000000004");
    private UUID profCarla;
    private UUID profPatricia;
    private UUID profInativa;
    private UUID pkgEnsaio;   // 60 min, R$ 50000, 7 dias de entrega
    private UUID contactId;
    private UUID conversationId;

    // 2026-07-01T11:00-03:00 (BRT) → dentro de 08:00–20:00.
    private static final Instant START = Instant.parse("2026-07-01T14:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'fotografia')",
            COMPANY, "Foto S", "foto-s");
        profCarla = UUID.randomUUID();
        profPatricia = UUID.randomUUID();
        profInativa = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name, specialty) values (?, ?, 'Carla', 'Social')",
            profCarla, COMPANY);
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name, specialty) values (?, ?, 'Patrícia', 'Ensaio')",
            profPatricia, COMPANY);
        jdbcTemplate.update("insert into fotografia_professionals (id, company_id, name, active) values (?, ?, 'Inativa', false)",
            profInativa, COMPANY);
        pkgEnsaio = UUID.randomUUID();
        jdbcTemplate.update("insert into fotografia_packages (id, company_id, name, duration_minutes, price_cents, delivery_days) "
            + "values (?, ?, 'Ensaio 1h', 60, 50000, 7)", pkgEnsaio, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990290", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private FotografiaSessionAppointment seedWithCarla() {
        return service.create(COMPANY, profCarla, pkgEnsaio, contactId, conversationId, START, "Marina", "+5511999990290", null);
    }

    @Test
    @DisplayName("create válida → agendada, snapshots profissional/cliente/pacote, duração+preço do pacote, end_at + delivery_due_date materializados")
    void create_agendada() {
        FotografiaSessionAppointment a = seedWithCarla();
        assertThat(a.status()).isEqualTo("agendada");
        assertThat(a.professionalName()).isEqualTo("Carla");
        assertThat(a.customerName()).isEqualTo("Marina");
        assertThat(a.packageName()).isEqualTo("Ensaio 1h");
        assertThat(a.priceCents()).isEqualTo(50000);
        assertThat(a.durationMinutes()).isEqualTo(60);
        assertThat(a.deliveryDays()).isEqualTo(7);
        // end_at MATERIALIZADO = start + 60 min.
        assertThat(a.endAt()).isEqualTo(START.plusSeconds(60 * 60L));
        // delivery_due_date MATERIALIZADA = dia local da sessão + 7 dias.
        LocalDate expectedDue = START.atZone(TENANT_ZONE).toLocalDate().plusDays(7);
        assertThat(a.deliveryDueDate()).isEqualTo(expectedDue);
    }

    @Test
    @DisplayName("create fora do horário (07:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T07:00-03:00 → 10:00 UTC = 07:00 BRT; antes de opens_at 08:00 BRT.
        Instant early = Instant.parse("2026-07-01T10:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, pkgEnsaio, contactId, null, early, "Marina", null, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com profissional inativo → InactiveProfessionalException (400)")
    void create_inactiveProfessional() {
        assertThatThrownBy(() -> service.create(COMPANY, profInativa, pkgEnsaio, contactId, null, START, "Marina", null, null))
            .isInstanceOf(InactiveProfessionalException.class);
    }

    @Test
    @DisplayName("conflito MESMO PROFISSIONAL (Carla, mesmo horário) → ConflictException (409)")
    void create_conflictSameProfessional() {
        seedWithCarla();   // Carla 11:00–12:00 BRT
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, pkgEnsaio, contactId, null, START, "Marina", null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("half-open: sessão que COMEÇA exatamente onde a outra TERMINA não conflita; parcial conflita")
    void create_halfOpenWindow() {
        seedWithCarla();   // Carla 11:00–12:00 BRT
        // Borda exata (12:00 = fim da primeira): janela half-open NÃO conflita — invariante do chassi A.
        FotografiaSessionAppointment adjacent = service.create(COMPANY, profCarla, pkgEnsaio, contactId,
            null, START.plusSeconds(60 * 60), "Marina", null, null);
        assertThat(adjacent.status()).isEqualTo("agendada");
        // Sobreposição parcial (11:30) → conflita.
        assertThatThrownBy(() -> service.create(COMPANY, profCarla, pkgEnsaio, contactId,
            null, START.plusSeconds(30 * 60), "Marina", null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("MESMO HORÁRIO, PROFISSIONAL DIFERENTE (Patrícia) → OK (paralelismo)")
    void create_sameSlotDifferentProfessional() {
        seedWithCarla();   // Carla 11:00 BRT
        assertThatCode(() -> service.create(COMPANY, profPatricia, pkgEnsaio, contactId, null, START, "Marina", null, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from fotografia_session_appointments where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("updateStatus agendada→confirmada → notifica com 'Sessão confirmada'")
    void confirm_notifies() {
        FotografiaSessionAppointment a = seedWithCarla();
        FotografiaSessionAppointment confirmed = service.updateStatus(COMPANY, a.id(), "confirmada");
        assertThat(confirmed.status()).isEqualTo("confirmada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Sessão confirmada").contains("às");
    }

    @Test
    @DisplayName("fluxo agendada→confirmada→realizada→entregue (realizada→entregue válida; entregue silenciosa)")
    void realizadaToEntregue() {
        FotografiaSessionAppointment a = seedWithCarla();
        service.updateStatus(COMPANY, a.id(), "confirmada");
        FotografiaSessionAppointment realizada = service.updateStatus(COMPANY, a.id(), "realizada");
        assertThat(realizada.status()).isEqualTo("realizada");
        FotografiaSessionAppointment entregue = service.updateStatus(COMPANY, a.id(), "entregue");
        assertThat(entregue.status()).isEqualTo("entregue");
        // só a confirmação notificou (1); realizada/entregue são silenciosas.
        assertThat(fakeEvolution.sent()).hasSize(1);
    }

    @Test
    @DisplayName("confirmada→cancelada notifica; cancelada→agendada inválida → 409")
    void invalidTransition() {
        FotografiaSessionAppointment a = seedWithCarla();
        service.updateStatus(COMPANY, a.id(), "confirmada");
        FotografiaSessionAppointment cancelled = service.updateStatus(COMPANY, a.id(), "cancelada");
        assertThat(cancelled.status()).isEqualTo("cancelada");
        // de uma cancelada (terminal) → agendada é inválida.
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
            return "key-foto";
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
