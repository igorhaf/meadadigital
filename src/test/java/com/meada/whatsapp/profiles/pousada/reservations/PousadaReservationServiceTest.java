package com.meada.whatsapp.profiles.pousada.reservations;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.ConflictException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.InactiveRoomException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.InvalidDatesException;
import com.meada.whatsapp.profiles.pousada.reservations.PousadaReservationService.OverCapacityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Testa o PousadaReservationService (camada 7.6) — os CRÍTICOS da SM:
 * create válida (snapshots), check_out<=check_in, check_in no passado, over-capacity, quarto
 * inativo, overlap mesmo quarto, ROTAÇÃO no mesmo dia (check-in == check-out de outra = OK), e
 * transição com notificação. EvolutionSender fake.
 */
@Import(PousadaReservationServiceTest.TestConfig.class)
class PousadaReservationServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PousadaReservationService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000003");
    private UUID roomStandard;
    private UUID contactId;
    private UUID conversationId;

    private final LocalDate ci = LocalDate.now().plusDays(10);
    private final LocalDate co = ci.plusDays(3);   // 3 noites

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pousada')",
            COMPANY, "Pousada S", "pousada-s");
        roomStandard = UUID.randomUUID();
        jdbcTemplate.update("insert into pousada_rooms (id, company_id, name, capacity, nightly_rate_cents) "
            + "values (?, ?, 'Standard', 2, 18000)", roomStandard, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990070", "Hóspede");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    private PousadaReservation seedReservation() {
        return service.create(COMPANY, roomStandard, contactId, conversationId, ci, co, 2, "Hóspede", "+5511999990070", null);
    }

    @Test
    @DisplayName("create válida → reservado, com snapshots de nights/total/capacity")
    void create_reservado() {
        PousadaReservation r = seedReservation();
        assertThat(r.status()).isEqualTo("reservado");
        assertThat(r.roomName()).isEqualTo("Standard");
        assertThat(r.nights()).isEqualTo(3);
        assertThat(r.nightlyRateCents()).isEqualTo(18000);
        assertThat(r.totalCents()).isEqualTo(54000);   // 18000 × 3
        assertThat(r.capacitySnapshot()).isEqualTo(2);
    }

    @Test
    @DisplayName("create com check_out <= check_in → InvalidDatesException")
    void create_invalidDates_order() {
        assertThatThrownBy(() -> service.create(COMPANY, roomStandard, null, null, ci, ci, 2, "X", null, null))
            .isInstanceOf(InvalidDatesException.class);
    }

    @Test
    @DisplayName("create com check_in no passado → InvalidDatesException")
    void create_invalidDates_past() {
        LocalDate past = LocalDate.now().minusDays(2);
        assertThatThrownBy(() -> service.create(COMPANY, roomStandard, null, null, past, past.plusDays(2), 2, "X", null, null))
            .isInstanceOf(InvalidDatesException.class);
    }

    @Test
    @DisplayName("create com guests_count > capacity → OverCapacityException")
    void create_overCapacity() {
        assertThatThrownBy(() -> service.create(COMPANY, roomStandard, null, null, ci, co, 5, "X", null, null))
            .isInstanceOf(OverCapacityException.class);
    }

    @Test
    @DisplayName("create com quarto inativo → InactiveRoomException")
    void create_inactiveRoom() {
        jdbcTemplate.update("update pousada_rooms set active = false where id = ?", roomStandard);
        assertThatThrownBy(() -> service.create(COMPANY, roomStandard, null, null, ci, co, 2, "X", null, null))
            .isInstanceOf(InactiveRoomException.class);
    }

    @Test
    @DisplayName("overlap MESMO QUARTO → ConflictException (409)")
    void create_conflictOverlap() {
        seedReservation();   // ci..co (3 noites)
        // novo intervalo que entra no meio → overlap.
        assertThatThrownBy(() -> service.create(COMPANY, roomStandard, null, null, ci.plusDays(1), co.plusDays(1), 2, "Outro", null, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("ROTAÇÃO: check-in EXATAMENTE no dia do check-out de outra → OK (half-open, chave da SM)")
    void create_sameDayRotation() {
        seedReservation();   // ci..co
        // nova reserva começa NO DIA do check-out anterior → NÃO conflita (half-open).
        assertThatCode(() -> service.create(COMPANY, roomStandard, null, null, co, co.plusDays(2), 2, "Próximo", null, null))
            .doesNotThrowAnyException();
        Long count = jdbcTemplate.queryForObject("select count(*) from pousada_reservations where company_id = ?",
            Long.class, COMPANY);
        assertThat(count).isEqualTo(2L);   // ambas coexistem; rotação no mesmo dia.
    }

    @Test
    @DisplayName("updateStatus reservado→confirmado → notifica com room_name + datas + total")
    void confirm_notifies() {
        PousadaReservation r = seedReservation();
        PousadaReservation confirmed = service.updateStatus(COMPANY, r.id(), "confirmado");
        assertThat(confirmed.status()).isEqualTo("confirmado");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text())
            .contains("confirmada").contains("Standard").contains("540,00");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-pousada";
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
