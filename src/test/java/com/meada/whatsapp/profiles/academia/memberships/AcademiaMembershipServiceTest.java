package com.meada.whatsapp.profiles.academia.memberships;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.AlreadyActiveException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.ClassFullException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.ClassInactiveException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.NoClassesException;
import com.meada.whatsapp.profiles.academia.memberships.AcademiaMembershipService.PlanInactiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o AcademiaMembershipService (camada 7.7) — os CRÍTICOS: create válida com snapshots, plan
 * inativo, class inativa, sem aulas, aula LOTADA (capacity 1), contato já ativo, cancelada
 * materializa end_date + notifica, e SUSPENSA mantém ocupando vaga (decisão 6). EvolutionSender fake.
 */
@Import(AcademiaMembershipServiceTest.TestConfig.class)
class AcademiaMembershipServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AcademiaMembershipService service;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("cc000000-0000-0000-0000-000000000004");
    private UUID plan;
    private UUID classFuncional;   // capacity 12
    private UUID classPilates;     // capacity 8
    private UUID conversationId;
    private UUID contactId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'academia')",
            COMPANY, "Academia S", "academia-s");
        plan = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_plans (id, company_id, name, monthly_cents) values (?, ?, 'Mensal Livre', 20000)",
            plan, COMPANY);
        classFuncional = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
            + "duration_minutes, capacity) values (?, ?, 'Funcional', 'funcional', 1, '07:00', 60, 12)", classFuncional, COMPANY);
        classPilates = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
            + "duration_minutes, capacity) values (?, ?, 'Pilates', 'pilates', 2, '09:00', 60, 8)", classPilates, COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990090", "Aluno");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
    }

    @Test
    @DisplayName("create válida com 2 aulas → ativa + 2 entries (snapshots)")
    void create_ok() {
        AcademiaMembership m = service.create(COMPANY, plan, List.of(classFuncional, classPilates),
            null, null, "Pedro", null, null);
        assertThat(m.status()).isEqualTo("ativa");
        assertThat(m.planName()).isEqualTo("Mensal Livre");
        assertThat(m.planMonthlyCents()).isEqualTo(20000);
        assertThat(m.classes()).hasSize(2);
    }

    @Test
    @DisplayName("create com plano inativo → PlanInactiveException")
    void create_planInactive() {
        jdbcTemplate.update("update academia_plans set active = false where id = ?", plan);
        assertThatThrownBy(() -> service.create(COMPANY, plan, List.of(classFuncional), null, null, "X", null, null))
            .isInstanceOf(PlanInactiveException.class);
    }

    @Test
    @DisplayName("create com aula inativa → ClassInactiveException")
    void create_classInactive() {
        jdbcTemplate.update("update academia_classes set active = false where id = ?", classFuncional);
        assertThatThrownBy(() -> service.create(COMPANY, plan, List.of(classFuncional), null, null, "X", null, null))
            .isInstanceOf(ClassInactiveException.class);
    }

    @Test
    @DisplayName("create com array de aulas vazio → NoClassesException")
    void create_noClasses() {
        assertThatThrownBy(() -> service.create(COMPANY, plan, List.of(), null, null, "X", null, null))
            .isInstanceOf(NoClassesException.class);
    }

    @Test
    @DisplayName("create em aula LOTADA (capacity 1, já 1) → ClassFullException (carrega className)")
    void create_classFull() {
        UUID tiny = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
            + "duration_minutes, capacity) values (?, ?, 'Spinning', 'spinning', 3, '19:00', 45, 1)", tiny, COMPANY);
        service.create(COMPANY, plan, List.of(tiny), null, null, "Primeiro", null, null);  // ocupa a única vaga
        assertThatThrownBy(() -> service.create(COMPANY, plan, List.of(tiny), null, null, "Segundo", null, null))
            .isInstanceOf(ClassFullException.class)
            .satisfies(e -> assertThat(((ClassFullException) e).className()).isEqualTo("Spinning"));
    }

    @Test
    @DisplayName("create com contato que já tem matrícula ativa → AlreadyActiveException")
    void create_alreadyActive() {
        service.create(COMPANY, plan, List.of(classFuncional), contactId, conversationId, "Aluno", null, null);
        assertThatThrownBy(() -> service.create(COMPANY, plan, List.of(classPilates), contactId, conversationId, "Aluno", null, null))
            .isInstanceOf(AlreadyActiveException.class);
    }

    @Test
    @DisplayName("updateStatus ativa→cancelada → end_date materializado + notifica despedida")
    void cancel_endDateAndNotify() {
        AcademiaMembership m = service.create(COMPANY, plan, List.of(classFuncional), contactId, conversationId, "Aluno", null, null);
        fakeEvolution.reset();   // descarta a notificação de boas-vindas.
        AcademiaMembership cancelled = service.updateStatus(COMPANY, m.id(), "cancelada");
        assertThat(cancelled.status()).isEqualTo("cancelada");
        assertThat(cancelled.endDate()).isNotNull();
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("cancelada");
    }

    @Test
    @DisplayName("SUSPENSA mantém ocupando vaga: capacity 1 + suspensa + nova matrícula → ClassFullException")
    void suspended_keepsSlot() {
        UUID tiny = UUID.randomUUID();
        jdbcTemplate.update("insert into academia_classes (id, company_id, name, modality, day_of_week, start_time, "
            + "duration_minutes, capacity) values (?, ?, 'Boxe', 'boxe', 5, '20:00', 60, 1)", tiny, COMPANY);
        AcademiaMembership a = service.create(COMPANY, plan, List.of(tiny), null, null, "Ocupante", null, null);
        // suspende → ainda ocupa a vaga (decisão 6).
        service.updateStatus(COMPANY, a.id(), "suspensa");
        assertThatThrownBy(() -> service.create(COMPANY, plan, List.of(tiny), null, null, "Novo", null, null))
            .isInstanceOf(ClassFullException.class);
        // cancela → libera a vaga.
        service.updateStatus(COMPANY, a.id(), "cancelada");
        AcademiaMembership freed = service.create(COMPANY, plan, List.of(tiny), null, null, "Liberado", null, null);
        assertThat(freed.status()).isEqualTo("ativa");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-aca";
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
