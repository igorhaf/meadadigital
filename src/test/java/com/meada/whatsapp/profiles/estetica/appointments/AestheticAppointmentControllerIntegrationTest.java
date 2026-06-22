package com.meada.whatsapp.profiles.estetica.appointments;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de agendamento (camada 8.3): POST avulso 201, conflito por profissional 409, ficha PUT/GET,
 * package_wrong_contact (POST manual com package), e profile guard 403. (O consumo/devolução de saldo é
 * coberto a fundo no service/handler test.)
 */
class AestheticAppointmentControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";
    private static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");

    private UUID companyId;
    private UUID prof1;
    private UUID prof2;
    private UUID procedureId;

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID cid = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, cid);
        return cid;
    }

    private void seedCatalog() {
        jdbcTemplate.update("insert into aesthetic_config (company_id, opens_at, closes_at, slot_minutes) "
            + "values (?, '08:00', '20:00', 30)", companyId);
        prof1 = UUID.randomUUID();
        prof2 = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_professionals (id, company_id, name) values (?, ?, 'Camila')", prof1, companyId);
        jdbcTemplate.update("insert into aesthetic_professionals (id, company_id, name) values (?, ?, 'Tatiane')", prof2, companyId);
        procedureId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_procedures (id, company_id, name, duration_minutes, unit_price_cents) "
            + "values (?, ?, 'Drenagem', 50, 12000)", procedureId, companyId);
    }

    private String startAt(int plusDays, int hour) {
        return LocalDate.now(TZ).plusDays(plusDays).atTime(hour, 0).atZone(TZ).toInstant().toString();
    }

    @Test
    @DisplayName("POST avulso 201; conflito por profissional 409; outro profissional mesmo horário 201; ficha PUT/GET")
    void avulsoConflictAndNote() throws Exception {
        UUID sub = UUID.randomUUID();
        companyId = seedTenant(sub, "estetica@test.dev", "estetica");
        seedCatalog();
        String t = mintValidToken("estetica@test.dev", sub);
        String when = startAt(1, 14);

        mockMvc.perform(post("/api/estetica/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof1 + "\",\"procedureId\":\"" + procedureId
                    + "\",\"guestName\":\"Marina\",\"startAt\":\"" + when + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.consumedSession").value(false));

        UUID apptId = jdbcTemplate.queryForObject(
            "select id from aesthetic_appointments where company_id = ? and professional_id = ?", UUID.class, companyId, prof1);

        // mesmo profissional, mesmo horário → conflito.
        mockMvc.perform(post("/api/estetica/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof1 + "\",\"procedureId\":\"" + procedureId
                    + "\",\"guestName\":\"Pedro\",\"startAt\":\"" + when + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("conflict_slot"));

        // outro profissional, mesmo horário → OK.
        mockMvc.perform(post("/api/estetica/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof2 + "\",\"procedureId\":\"" + procedureId
                    + "\",\"guestName\":\"Pedro\",\"startAt\":\"" + when + "\"}"))
            .andExpect(status().isCreated());

        // ficha PUT + GET.
        mockMvc.perform(put("/api/estetica/appointments/" + apptId + "/note").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"treatedArea\":\"abdômen\",\"observations\":\"primeira sessão\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.treatedArea").value("abdômen"));
        mockMvc.perform(get("/api/estetica/appointments/" + apptId + "/note").header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.observations").value("primeira sessão"));
    }

    @Test
    @DisplayName("POST manual com package_id (sem contato) → 403 package_wrong_contact")
    void manualWithPackageWrongContact() throws Exception {
        UUID sub = UUID.randomUUID();
        companyId = seedTenant(sub, "estetica@test.dev", "estetica");
        seedCatalog();
        String t = mintValidToken("estetica@test.dev", sub);
        // pacote ativo de um contato qualquer.
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511999990340", "Marina");
        UUID pkg = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into aesthetic_packages (id, company_id, contact_id, procedure_id, customer_name, "
                + "procedure_name, unit_price_cents, total_sessions, sessions_used, sessions_remaining, total_cents, status, activated_at) "
                + "values (?, ?, ?, ?, 'Marina', 'Drenagem', 12000, 10, 0, 10, 120000, 'ativo', now())",
            pkg, companyId, contactId, procedureId);

        // POST manual não tem contactId → o pacote é de outro "contato" (null) → wrong_contact.
        mockMvc.perform(post("/api/estetica/appointments").header("Authorization", "Bearer " + t)
                .contentType(JSON).content("{\"professionalId\":\"" + prof1 + "\",\"procedureId\":\"" + procedureId
                    + "\",\"packageId\":\"" + pkg + "\",\"guestName\":\"Marina\",\"startAt\":\"" + startAt(1, 9) + "\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("package_wrong_contact"));
    }

    @Test
    @DisplayName("tenant de OUTRO perfil → 403 forbidden_wrong_profile")
    void wrongProfile() throws Exception {
        UUID sub = UUID.randomUUID();
        seedTenant(sub, "salon@test.dev", "salon");
        String t = mintValidToken("salon@test.dev", sub);
        mockMvc.perform(get("/api/estetica/appointments").header("Authorization", "Bearer " + t))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.reason").value("forbidden_wrong_profile"));
    }
}
