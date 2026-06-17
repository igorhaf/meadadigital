package com.meada.whatsapp.profiles.pousada.rooms;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.pousada.rooms.PousadaRoomService.RoomInUseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o PousadaRoomService (camada 7.6): create+audit, toggle, delete em uso → 409.
 */
class PousadaRoomServiceTest extends AbstractIntegrationTest {

    @Autowired
    private PousadaRoomService service;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("db000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'pousada')",
            COMPANY, "Pousada Teste", "pousada-teste");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@pousada.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("create válido → persiste + audita pousada_room_created")
    void create_persistsAndAudits() {
        PousadaRoom r = service.create(COMPANY, USER, "Standard", 2, 18000, "Casal", null);
        assertThat(r.name()).isEqualTo("Standard");
        assertThat(r.capacity()).isEqualTo(2);
        assertThat(r.nightlyRateCents()).isEqualTo(18000);
        Long audit = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'pousada_room_created' and entity_id = ?",
            Long.class, r.id());
        assertThat(audit).isEqualTo(1L);
    }

    @Test
    @DisplayName("toggle desliga active")
    void toggle() {
        PousadaRoom r = service.create(COMPANY, USER, "Suíte", 2, 65000, null, null);
        PousadaRoom off = service.toggle(COMPANY, USER, r.id(), false);
        assertThat(off.active()).isFalse();
    }

    @Test
    @DisplayName("delete de quarto com reserva → RoomInUseException (409)")
    void delete_inUse() {
        PousadaRoom r = service.create(COMPANY, USER, "Família", 5, 42000, null, null);
        LocalDate ci = LocalDate.now().plusDays(10);
        LocalDate co = ci.plusDays(3);
        jdbcTemplate.update(
            "insert into pousada_reservations (company_id, room_id, guest_name, guests_count, "
                + "check_in_date, check_out_date, nights, room_name, nightly_rate_cents, capacity_snapshot, total_cents) "
                + "values (?, ?, 'Hóspede', 2, ?, ?, 3, 'Família', 42000, 5, 126000)",
            COMPANY, r.id(), java.sql.Date.valueOf(ci), java.sql.Date.valueOf(co));

        assertThatThrownBy(() -> service.delete(COMPANY, USER, r.id()))
            .isInstanceOf(RoomInUseException.class);
    }
}
