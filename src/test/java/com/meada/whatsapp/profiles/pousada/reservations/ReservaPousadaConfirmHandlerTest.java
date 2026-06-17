package com.meada.whatsapp.profiles.pousada.reservations;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o ReservaPousadaConfirmHandler (camada 7.6): parse OK + create, room inválido → empty,
 * sem tag → empty. Tag namespace distinto: <reserva_pousada> (não <reserva> do RestaurantBot).
 */
class ReservaPousadaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private ReservaPousadaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000004");
    private UUID conversationId;
    private UUID contactId;
    private UUID roomId;

    private final LocalDate ci = LocalDate.now().plusDays(15);
    private final LocalDate co = ci.plusDays(2);

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pousada')",
            COMPANY, "Pousada H", "pousada-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990080", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        roomId = UUID.randomUUID();
        jdbcTemplate.update("insert into pousada_rooms (id, company_id, name, capacity, nightly_rate_cents) "
            + "values (?, ?, 'Standard', 2, 18000)", roomId, COMPANY);
    }

    @Test
    @DisplayName("tag <reserva_pousada> válida → cria reservado (guest_name do JSON)")
    void parseAndCreate_ok() {
        String aiText = "Perfeito, Marina! Reservei o Standard de " + ci + " a " + co + ", total R$ 360. Aguardamos você!\n"
            + "<reserva_pousada>{\"room_id\":\"" + roomId + "\",\"check_in\":\"" + ci + "\",\"check_out\":\""
            + co + "\",\"guests_count\":2,\"guest_name\":\"Marina\",\"notes\":\"\"}</reserva_pousada>";

        Optional<PousadaReservation> r = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(r).isPresent();
        assertThat(r.get().status()).isEqualTo("reservado");
        assertThat(r.get().roomName()).isEqualTo("Standard");
        assertThat(r.get().nights()).isEqualTo(2);
        assertThat(r.get().totalCents()).isEqualTo(36000);
        assertThat(r.get().guestName()).isEqualTo("Marina");
    }

    @Test
    @DisplayName("room_id inexistente na tag → Optional.empty (não criado)")
    void parseAndCreate_invalidRoom() {
        String aiText = "Reservado!\n<reserva_pousada>{\"room_id\":\"" + UUID.randomUUID()
            + "\",\"check_in\":\"" + ci + "\",\"check_out\":\"" + co + "\",\"guests_count\":2}</reserva_pousada>";
        Optional<PousadaReservation> r = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);
        assertThat(r).isEmpty();
        Long count = jdbcTemplate.queryForObject("select count(*) from pousada_reservations", Long.class);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<PousadaReservation> r = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer reservar um quarto?");
        assertThat(r).isEmpty();
    }
}
