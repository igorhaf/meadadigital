package com.meada.profiles.barbearia.queue;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints da FILA (camada 8.1): POST entra, GET fila com posição, PATCH chamar/atendido,
 * 409 queue_disabled, profile guard 403.
 */
class BarberQueueControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    @Test
    @DisplayName("POST entra na fila → 201 aguardando posição 1; GET lista com waiting; PATCH chamar → chamado")
    void enqueueAndCall() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        jdbcTemplate.update("insert into barber_config (company_id, queue_enabled) values (?, true)", companyId);
        UUID marcelo = UUID.randomUUID();
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", marcelo, companyId);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);

        String body = "{\"barberId\":\"" + marcelo + "\",\"serviceId\":\"" + svc + "\",\"guestName\":\"Cli\"}";
        mockMvc.perform(post("/api/barbearia/queue").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("aguardando"))
            .andExpect(jsonPath("$.position").value(1));

        UUID ticket = jdbcTemplate.queryForObject("select id from barber_queue_tickets where company_id = ?", UUID.class, companyId);

        mockMvc.perform(get("/api/barbearia/queue").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.waiting").value(1))
            .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(patch("/api/barbearia/queue/" + ticket + "/status").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"newStatus\":\"chamado\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("chamado"));
    }

    @Test
    @DisplayName("queue_enabled=false → POST fila → 409 queue_disabled")
    void enqueueDisabled() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        jdbcTemplate.update("insert into barber_config (company_id, queue_enabled) values (?, false)", companyId);
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);
        mockMvc.perform(post("/api/barbearia/queue").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"serviceId\":\"" + svc + "\",\"guestName\":\"X\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("queue_disabled"));
    }

    @Test
    @DisplayName("tenant NÃO-barbearia (pet) → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "pet@test.dev", "pet");
        String t = mintValidToken("pet@test.dev", sub);
        mockMvc.perform(get("/api/barbearia/queue").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }

    @Test
    @DisplayName("convert: 1º ticket vira atendimento (201); 2º do MESMO barbeiro no mesmo instante → 409 conflict_slot")
    void convertAndSlotConflict() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "barber@test.dev", "barbearia");
        String t = mintValidToken("barber@test.dev", sub);
        // Janela cheia: o convert agenda em Instant.now() — sem isso o default 09-20h
        // flakearia o teste em runs fora do horário comercial (fuso SP).
        jdbcTemplate.update("insert into barber_config (company_id, queue_enabled, opens_at, closes_at) "
            + "values (?, true, '00:00', '23:59:59')", companyId);
        UUID marcelo = UUID.randomUUID();
        UUID svc = UUID.randomUUID();
        jdbcTemplate.update("insert into barber_barbers (id, company_id, name) values (?, ?, 'Marcelo')", marcelo, companyId);
        jdbcTemplate.update("insert into barber_services (id, company_id, name, duration_minutes) values (?, ?, 'Corte', 30)", svc, companyId);

        mockMvc.perform(post("/api/barbearia/queue").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"barberId\":\"" + marcelo + "\",\"serviceId\":\"" + svc + "\",\"guestName\":\"Cli 1\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/barbearia/queue").header("Authorization", "Bearer " + t)
                .contentType(JSON)
                .content("{\"barberId\":\"" + marcelo + "\",\"serviceId\":\"" + svc + "\",\"guestName\":\"Cli 2\"}"))
            .andExpect(status().isCreated());

        UUID ticket1 = jdbcTemplate.queryForObject(
            "select id from barber_queue_tickets where company_id = ? and guest_name = 'Cli 1'", UUID.class, companyId);
        UUID ticket2 = jdbcTemplate.queryForObject(
            "select id from barber_queue_tickets where company_id = ? and guest_name = 'Cli 2'", UUID.class, companyId);

        mockMvc.perform(post("/api/barbearia/queue/" + ticket1 + "/convert").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("agendado"));

        // Mesmo barbeiro, mesmo instante → SlotConflict → ConflictException TIPADA → 409
        // conflict_slot. (Antes o catch genérico de RuntimeException mascarava qualquer
        // erro inesperado como conflict_slot; agora só o conflito real cai aqui.)
        mockMvc.perform(post("/api/barbearia/queue/" + ticket2 + "/convert").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"));
    }
}
