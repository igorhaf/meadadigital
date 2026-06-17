package com.meada.whatsapp.profiles.academia.memberships;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o MatriculaConfirmHandler (camada 7.7): parse OK + cria; sem tag → empty. Tag <matricula>.
 */
class MatriculaConfirmHandlerTest extends AbstractIntegrationTest {

    @Autowired
    private MatriculaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000006");
    private UUID conversationId;
    private UUID contactId;
    private UUID plan;
    private UUID cls;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia H", "academia-h");
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990095", "Lucas");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        plan = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal', 20000)", plan, COMPANY);
        cls = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
            + "duration_minutes, capacity) values (?, ?, 'Funcional', 'funcional', 1, '07:00', 60, 12)", cls, COMPANY);
    }

    @Test
    @DisplayName("tag <matricula> válida → cria matrícula ativa (student_name do JSON)")
    void parseAndCreate_ok() {
        String aiText = "Pronto, Lucas! Matriculei você no Mensal, na aula Funcional. Bom treino!\n"
            + "<matricula>{\"plan_id\":\"" + plan + "\",\"class_ids\":[\"" + cls + "\"],\"student_name\":\"Lucas\"}</matricula>";

        Optional<AcademiaMembership> m = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(m).isPresent();
        assertThat(m.get().status()).isEqualTo("ativa");
        assertThat(m.get().planName()).isEqualTo("Mensal");
        assertThat(m.get().classes()).hasSize(1);
        assertThat(m.get().studentName()).isEqualTo("Lucas");
    }

    @Test
    @DisplayName("texto sem tag → Optional.empty (conversa normal)")
    void parseAndCreate_noTag() {
        Optional<AcademiaMembership> m = handler.parseAndCreate(
            COMPANY, conversationId, contactId, "Oi! Quer conhecer nossos planos?");
        assertThat(m).isEmpty();
    }
}
