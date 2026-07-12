package com.meada.profiles.restaurant.reservations;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.restaurant.reservations.ReservationService.ConflictException;
import com.meada.profiles.restaurant.reservations.ReservationService.InvalidStatusTransitionException;
import com.meada.profiles.restaurant.reservations.ReservationService.OutsideHoursException;
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
 * Testa o ReservationService (camada 7.3): create válida → pendente, fora do horário, conflito de
 * slot, transição com notificação (confirmada) e transição silenciosa (realizada). EvolutionSender
 * é um fake que registra os envios.
 */
@Import(ReservationServiceTest.TestConfig.class)
class ReservationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c8000000-0000-0000-0000-000000000002");
    private UUID tableId;
    private UUID contactId;
    private UUID conversationId;

    // 2026-07-01T20:00-03:00 (BRT) → dentro da janela 11:00–23:00; dura 2h (até 22:00 BRT).
    private static final Instant START = Instant.parse("2026-07-01T23:00:00Z");

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'restaurant')",
            COMPANY, "Rest O", "rest-o");
        tableId = UUID.randomUUID();
        jdbcTemplate.update("insert into restaurant_tables (id, company_id, label, capacity) values (?, ?, 'Mesa 1', 4)",
            tableId, COMPANY);
        // Conversa/contato (p/ a notificação ter canal resolúvel).
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990010", "Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private Reservation seedReservation() {
        return service.create(COMPANY, tableId, conversationId, contactId, "Cliente", "+5511999990010",
            START, 4, null);
    }

    @Test
    @DisplayName("create válida → status pendente")
    void create_pendente() {
        Reservation r = seedReservation();
        assertThat(r.status()).isEqualTo("pendente");
        assertThat(r.tableLabel()).isEqualTo("Mesa 1");
        assertThat(r.numPeople()).isEqualTo(4);
    }

    @Test
    @DisplayName("create fora do horário (08:00 BRT) → OutsideHoursException (400)")
    void create_outsideHours() {
        // 2026-07-01T08:00-03:00 → 11:00 UTC; antes de opens_at 11:00 BRT.
        Instant early = Instant.parse("2026-07-01T11:00:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, tableId, null, null, "C", null, early, 2, null))
            .isInstanceOf(OutsideHoursException.class);
    }

    @Test
    @DisplayName("create com conflito (mesma mesa, janela sobreposta) → ConflictException (409)")
    void create_conflict() {
        seedReservation();   // 20:00–22:00 BRT
        // 20:30 BRT (= 23:30 UTC) sobrepõe a reserva existente na mesma mesa.
        Instant overlap = Instant.parse("2026-07-01T23:30:00Z");
        assertThatThrownBy(() -> service.create(COMPANY, tableId, null, null, "Outro", null, overlap, 2, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("half-open: reserva que COMEÇA exatamente onde a outra TERMINA não conflita; parcial conflita")
    void create_halfOpenWindow() {
        // Base própria à tarde (14:00–16:00 BRT = 17:00 UTC) pra não esbarrar no fechamento.
        Instant base = Instant.parse("2026-07-01T17:00:00Z");
        service.create(COMPANY, tableId, null, null, "Base", null, base, 2, null);
        // Borda exata (16:00 BRT = fim da primeira): janela half-open NÃO conflita — invariante do chassi A.
        Reservation adjacent = service.create(COMPANY, tableId, null, null, "Borda", null,
            base.plusSeconds(2 * 60 * 60), 2, null);
        assertThat(adjacent.status()).isEqualTo("pendente");
        // Sobreposição parcial (15:00 BRT) → conflita.
        assertThatThrownBy(() -> service.create(COMPANY, tableId, null, null, "Parcial", null,
            base.plusSeconds(60 * 60), 2, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("updateStatus pendente→confirmada → notifica o cliente")
    void confirm_notifies() {
        Reservation r = seedReservation();
        Reservation confirmed = service.updateStatus(COMPANY, r.id(), "confirmada");
        assertThat(confirmed.status()).isEqualTo("confirmada");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("confirmada");
    }

    @Test
    @DisplayName("updateStatus confirmada→realizada → silencioso (sem notificação)")
    void realizada_silent() {
        Reservation r = seedReservation();
        service.updateStatus(COMPANY, r.id(), "confirmada");
        fakeEvolution.reset();   // descarta a notificação da confirmação.
        Reservation done = service.updateStatus(COMPANY, r.id(), "realizada");
        assertThat(done.status()).isEqualTo("realizada");
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("transição inválida (pendente→realizada) → InvalidStatusTransitionException (409)")
    void invalidTransition() {
        Reservation r = seedReservation();
        assertThatThrownBy(() -> service.updateStatus(COMPANY, r.id(), "realizada"))
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
            return "key-rest";
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
