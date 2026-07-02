package com.meada.profiles.academia.checkins;

import com.meada.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa os endpoints de check-in / frequência (camada 7.7, feature #4): POST registra a presença de
 * hoje + GET lista; UNIQUE (membership, class, dia) impede duplicata no mesmo dia (409).
 */
class AcademiaCheckinControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final String JSON = "application/json";

    private UUID seedTenant(UUID sub, String email, String profileId) {
        UUID companyId = seedTenantAdmin(email, sub);
        jdbcTemplate.update("update companies set profile_id = ? where id = ?", profileId, companyId);
        return companyId;
    }

    private UUID[] seedMembershipAndClass(UUID companyId) {
        UUID plan = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal', 20000)", plan, companyId);
        UUID clazz = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, duration_minutes, capacity) "
            + "values (?, ?, 'Funcional', 'funcional', 2, '19:00', 60, 20)", clazz, companyId);
        UUID memb = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_memberships (id, company_id, plan_id, student_name, plan_name, plan_monthly_cents, start_date) "
            + "values (?, ?, ?, 'Aluno', 'Mensal', 20000, current_date)", memb, companyId, plan);
        return new UUID[]{memb, clazz};
    }

    @Test
    @DisplayName("POST registra check-in → 201; GET lista mostra 1; 2º POST no dia → 409 duplicate_checkin")
    void registerAndListAndDuplicate() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca-checkin@test.dev", "academia");
        String t = mintValidToken("aca-checkin@test.dev", sub);
        UUID[] ids = seedMembershipAndClass(companyId);
        UUID memb = ids[0];
        UUID clazz = ids[1];

        String body = "{\"membershipId\":\"" + memb + "\",\"classId\":\"" + clazz + "\",\"source\":\"painel\"}";

        mockMvc.perform(post("/api/academia/checkins").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.source").value("painel"))
            .andExpect(jsonPath("$.membershipId").value(memb.toString()));

        mockMvc.perform(get("/api/academia/checkins?classId=" + clazz).header("Authorization", "Bearer " + t))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1));

        // 2ª presença no mesmo dia (mesma matrícula + aula) → UNIQUE bloqueia.
        mockMvc.perform(post("/api/academia/checkins").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.reason").value("duplicate_checkin"));
    }

    @Test
    @DisplayName("fidelidade ligada + matrícula com contato → check-in credita points_per_checkin (duplicata não dobra)")
    void checkinCreditsLoyaltyPoints() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca-checkin-fid@test.dev", "academia");
        String t = mintValidToken("aca-checkin-fid@test.dev", sub);
        UUID[] ids = seedMembershipAndClass(companyId);
        UUID memb = ids[0];
        UUID clazz = ids[1];

        // Vincula um contato à matrícula e liga a fidelidade (2 pontos por check-in).
        UUID contact = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, '+5511990002233', 'Aluno Fiel')",
            contact, companyId);
        jdbcTemplate.update("update academia_memberships set contact_id = ? where id = ?", contact, memb);
        jdbcTemplate.update(
            "insert into academia_loyalty_config (company_id, enabled, points_per_checkin) values (?, true, 2)",
            companyId);

        String body = "{\"membershipId\":\"" + memb + "\",\"classId\":\"" + clazz + "\"}";
        mockMvc.perform(post("/api/academia/checkins").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isCreated());

        Integer points = jdbcTemplate.queryForObject(
            "select points from academia_loyalty where company_id = ? and contact_id = ?",
            Integer.class, companyId, contact);
        org.assertj.core.api.Assertions.assertThat(points).isEqualTo(2);

        // Duplicata do dia → 409 ANTES do crédito: o saldo não dobra.
        mockMvc.perform(post("/api/academia/checkins").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isConflict());
        Integer after = jdbcTemplate.queryForObject(
            "select points from academia_loyalty where company_id = ? and contact_id = ?",
            Integer.class, companyId, contact);
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(2);
    }

    @Test
    @DisplayName("fidelidade DESLIGADA (sem config) → check-in não credita nada")
    void checkinWithoutLoyaltyConfigCreditsNothing() throws Exception {
        UUID sub = UUID.randomUUID();
        UUID companyId = seedTenant(sub, "aca-checkin-off@test.dev", "academia");
        String t = mintValidToken("aca-checkin-off@test.dev", sub);
        UUID[] ids = seedMembershipAndClass(companyId);
        UUID contact = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, '+5511990003344', 'Aluno')",
            contact, companyId);
        jdbcTemplate.update("update academia_memberships set contact_id = ? where id = ?", contact, ids[0]);

        String body = "{\"membershipId\":\"" + ids[0] + "\",\"classId\":\"" + ids[1] + "\"}";
        mockMvc.perform(post("/api/academia/checkins").header("Authorization", "Bearer " + t)
                .contentType(JSON).content(body))
            .andExpect(status().isCreated());

        Integer rows = jdbcTemplate.queryForObject(
            "select count(*) from academia_loyalty where company_id = ?", Integer.class, companyId);
        org.assertj.core.api.Assertions.assertThat(rows).isZero();
    }
}
