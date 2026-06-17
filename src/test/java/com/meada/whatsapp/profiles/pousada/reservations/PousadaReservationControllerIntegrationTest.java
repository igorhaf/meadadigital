package com.meada.whatsapp.profiles.pousada.reservations;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de reservas (camada 7.6): list+filtro, POST 409 conflict_dates, 400
 * over_capacity, ROTAÇÃO no mesmo dia → 201 (chave da SM), PATCH status.
 */
class PousadaReservationControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID seedRoom(UUID companyId, String name, int capacity) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into pousada_rooms (id, company_id, name, capacity, nightly_rate_cents) "
            + "values (?, ?, ?, ?, 18000)", id, companyId, name, capacity);
        return id;
    }

    private final LocalDate ci = LocalDate.now().plusDays(20);
    private final LocalDate co = ci.plusDays(3);

    @Test
    @DisplayName("POST cria → 201 reservado (nights/total); GET filtro por quarto mostra 1")
    void createAndFilter() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        UUID room = seedRoom(companyId, "Standard", 2);

        mockMvc.perform(post("/api/pousada/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"roomId\":\"" + room + "\",\"guestName\":\"Marina\",\"guestsCount\":2,"
                    + "\"checkIn\":\"" + ci + "\",\"checkOut\":\"" + co + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("reservado"))
            .andExpect(jsonPath("$.nights").value(3))
            .andExpect(jsonPath("$.totalCents").value(54000));

        mockMvc.perform(get("/api/pousada/reservations?roomId=" + room).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("POST overlap mesmo quarto → 409 conflict_dates (com detalhes); over_capacity → 400")
    void conflictAndCapacity() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        UUID room = seedRoom(companyId, "Standard", 2);

        mockMvc.perform(post("/api/pousada/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"roomId\":\"" + room + "\",\"guestName\":\"Marina\",\"guestsCount\":2,"
                    + "\"checkIn\":\"" + ci + "\",\"checkOut\":\"" + co + "\"}"))
            .andExpect(status().isCreated());

        // overlap (entra no meio) → 409 conflict_dates.
        mockMvc.perform(post("/api/pousada/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"roomId\":\"" + room + "\",\"guestName\":\"Outro\",\"guestsCount\":2,"
                    + "\"checkIn\":\"" + ci.plusDays(1) + "\",\"checkOut\":\"" + co.plusDays(1) + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_dates"))
            .andExpect(jsonPath("$.conflict.guestName").value("Marina"));

        // over_capacity (3 > cap 2) → 400.
        mockMvc.perform(post("/api/pousada/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"roomId\":\"" + room + "\",\"guestName\":\"Grupo\",\"guestsCount\":3,"
                    + "\"checkIn\":\"" + co.plusDays(5) + "\",\"checkOut\":\"" + co.plusDays(7) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.reason").value("over_capacity"));
    }

    @Test
    @DisplayName("ROTAÇÃO: check-in NO DIA do check-out de outra → 201 (half-open, chave da SM)")
    void sameDayRotation() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        UUID room = seedRoom(companyId, "Standard", 2);

        mockMvc.perform(post("/api/pousada/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"roomId\":\"" + room + "\",\"guestName\":\"Marina\",\"guestsCount\":2,"
                    + "\"checkIn\":\"" + ci + "\",\"checkOut\":\"" + co + "\"}"))
            .andExpect(status().isCreated());

        // nova reserva começa NO DIA do check-out anterior (co) → NÃO conflita.
        mockMvc.perform(post("/api/pousada/reservations").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"roomId\":\"" + room + "\",\"guestName\":\"Próximo\",\"guestsCount\":2,"
                    + "\"checkIn\":\"" + co + "\",\"checkOut\":\"" + co.plusDays(2) + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("reservado"));
    }

    @Test
    @DisplayName("PATCH status reservado→confirmado → 200; transição inválida → 409")
    void patchStatus() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "pousada@test.dev", "pousada");
        String t = mintValidToken("pousada@test.dev", sub);
        UUID room = seedRoom(companyId, "Standard", 2);

        UUID resId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into pousada_reservations (id, company_id, room_id, guest_name, guests_count, "
                + "check_in_date, check_out_date, nights, room_name, nightly_rate_cents, capacity_snapshot, total_cents) "
                + "values (?, ?, ?, 'Marina', 2, ?, ?, 3, 'Standard', 18000, 2, 54000)",
            resId, companyId, room, java.sql.Date.valueOf(ci), java.sql.Date.valueOf(co));

        mockMvc.perform(patch("/api/pousada/reservations/" + resId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"confirmado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("confirmado"));

        // confirmado → reservado é inválida.
        mockMvc.perform(patch("/api/pousada/reservations/" + resId + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"reservado\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("invalid_status_transition"));
    }
}
