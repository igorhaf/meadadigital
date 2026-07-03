package com.meada.profiles.barbearia.appointments;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
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
 * Testa o ConfirmacaoBarbeariaHandler (onda 1, backlog #1): a decisão do CLIENTE (SIM/CANCELAR ao
 * lembrete) muta o agendamento via a máquina de status existente. Barreiras: contato divergente →
 * no-op; transição inválida (confirmar um cancelado) → no-op; JSON/decisão inválidos → no-op.
 */
@Import(ConfirmacaoBarbeariaHandlerTest.TestConfig.class)
class ConfirmacaoBarbeariaHandlerTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-0000000000b4");
    private static final Instant START = Instant.parse("2026-07-10T15:00:00Z");

    @Autowired
    private ConfirmacaoBarbeariaHandler handler;
    @Autowired
    private BarberAppointmentService appointmentService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID barberId;
    private UUID serviceId;
    private UUID contactId;
    private UUID otherContactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'barbearia')",
            COMPANY, "Barbearia Cf", "barbearia-cf");
        barberId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        otherContactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')",
            barberId, COMPANY);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes, price_cents) "
            + "values (?, ?, 'Corte', 30, 4000)", serviceId, COMPANY);
        UUID instance = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst-cf", "tok-cf");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Cliente')",
            contactId, COMPANY, "+5511999990185");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Outro')",
            otherContactId, COMPANY, "+5511999990186");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private BarberAppointment seedAgendado() {
        return appointmentService.create(COMPANY, barberId, serviceId, contactId, conversationId,
            START, "Cliente", "+5511999990185", null, null);
    }

    private static String tag(UUID appointmentId, String decisao) {
        return "Perfeito! <confirmacao_barbearia>{\"appointment_id\":\"" + appointmentId
            + "\",\"decisao\":\"" + decisao + "\"}</confirmacao_barbearia>";
    }

    @Test
    @DisplayName("SIM do cliente → agendado vira confirmado (e o cliente recebe a notificação)")
    void sim_confirms() {
        BarberAppointment a = seedAgendado();
        Optional<BarberAppointment> updated = handler.parseAndApply(COMPANY, conversationId, contactId,
            tag(a.id(), "confirmado"));
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).isNotEmpty();   // notificação de confirmado.
    }

    @Test
    @DisplayName("CANCELAR do cliente → cancelado (libera o slot); strip remove a tag")
    void cancelar_cancels() {
        BarberAppointment a = seedAgendado();
        String text = tag(a.id(), "cancelado");
        Optional<BarberAppointment> updated = handler.parseAndApply(COMPANY, conversationId, contactId, text);
        assertThat(updated).isPresent();
        assertThat(updated.get().status()).isEqualTo("cancelado");
        assertThat(handler.stripConfirmacaoTag(text)).isEqualTo("Perfeito!");
    }

    @Test
    @DisplayName("BARREIRA: contato divergente → no-op (o horário de outro cliente não é tocado)")
    void wrongContact_noop() {
        BarberAppointment a = seedAgendado();
        Optional<BarberAppointment> updated = handler.parseAndApply(COMPANY, conversationId, otherContactId,
            tag(a.id(), "cancelado"));
        assertThat(updated).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select status from barber_appointments where id = ?",
            String.class, a.id())).isEqualTo("agendado");
    }

    @Test
    @DisplayName("transição inválida (confirmar um cancelado) e decisão desconhecida → no-op")
    void invalidTransitionOrDecision_noop() {
        BarberAppointment a = seedAgendado();
        handler.parseAndApply(COMPANY, conversationId, contactId, tag(a.id(), "cancelado"));

        assertThat(handler.parseAndApply(COMPANY, conversationId, contactId, tag(a.id(), "confirmado"))).isEmpty();
        assertThat(handler.parseAndApply(COMPANY, conversationId, contactId, tag(a.id(), "faltou"))).isEmpty();
        assertThat(handler.parseAndApply(COMPANY, conversationId, contactId, "sem tag nenhuma")).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-barber-cf";
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
