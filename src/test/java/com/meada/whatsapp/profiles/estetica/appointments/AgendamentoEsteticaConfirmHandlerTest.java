package com.meada.whatsapp.profiles.estetica.appointments;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o AgendamentoEsteticaConfirmHandler (camada 8.3): cria agendamento pela tag; consome saldo se
 * package_id; sem tag → empty; conflito → empty.
 */
@Import(AgendamentoEsteticaConfirmHandlerTest.TestConfig.class)
class AgendamentoEsteticaConfirmHandlerTest extends AbstractIntegrationTest {

    private static final ZoneId TZ = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private AgendamentoEsteticaConfirmHandler handler;

    private static final UUID COMPANY = UUID.fromString("cf000000-0000-0000-0000-000000000003");
    private UUID conversationId;
    private UUID contactId;
    private UUID prof;
    private UUID procedureId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'estetica')",
            COMPANY, "Estetica H1", "estetica-h1");
        jdbcTemplate.update("insert into aesthetic_config (company_id, opens_at, closes_at, slot_minutes) "
            + "values (?, '08:00', '20:00', 30)", COMPANY);
        UUID instance = UUID.randomUUID();
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, COMPANY, "+5511999990320", "Marina");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, instance);
        prof = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_professionals (id, company_id, name) values (?, ?, 'Camila')", prof, COMPANY);
        procedureId = UUID.randomUUID();
        jdbcTemplate.update("insert into aesthetic_procedures (id, company_id, name, duration_minutes, unit_price_cents) "
            + "values (?, ?, 'Drenagem', 50, 12000)", procedureId, COMPANY);
    }

    private UUID seedActivePackage(int total, int used) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into aesthetic_packages (id, company_id, contact_id, procedure_id, customer_name, "
                + "procedure_name, unit_price_cents, total_sessions, sessions_used, sessions_remaining, "
                + "total_cents, status, activated_at) "
                + "values (?, ?, ?, ?, 'Marina', 'Drenagem', 12000, ?, ?, ?, ?, 'ativo', now())",
            id, COMPANY, contactId, procedureId, total, used, total - used, total * 12000);
        return id;
    }

    @Test
    @DisplayName("tag com package_id ativo → cria agendamento + consome saldo")
    void parseAndCreate_consumesPackage() {
        UUID pkg = seedActivePackage(10, 0);
        String date = LocalDate.now(TZ).plusDays(1).toString();
        String aiText = "Agendado!\n<agendamento_estetica>{\"professional_id\":\"" + prof + "\","
            + "\"procedure_id\":\"" + procedureId + "\",\"date\":\"" + date + "\",\"start_time\":\"10:00\","
            + "\"package_id\":\"" + pkg + "\"}</agendamento_estetica>";

        Optional<AestheticAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().consumedSession()).isTrue();
        int remaining = jdbcTemplate.queryForObject("select sessions_remaining from aesthetic_packages where id = ?", Integer.class, pkg);
        assertThat(remaining).isEqualTo(9);
    }

    @Test
    @DisplayName("tag avulsa (package_id null) → cria agendamento sem consumir")
    void parseAndCreate_avulso() {
        String date = LocalDate.now(TZ).plusDays(1).toString();
        String aiText = "Agendado!\n<agendamento_estetica>{\"professional_id\":\"" + prof + "\","
            + "\"procedure_id\":\"" + procedureId + "\",\"date\":\"" + date + "\",\"start_time\":\"11:00\","
            + "\"package_id\":null}</agendamento_estetica>";

        Optional<AestheticAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, aiText);

        assertThat(a).isPresent();
        assertThat(a.get().consumedSession()).isFalse();
    }

    @Test
    @DisplayName("sem tag → Optional.empty")
    void parseAndCreate_noTag() {
        Optional<AestheticAppointment> a = handler.parseAndCreate(COMPANY, conversationId, contactId, "Oi, tudo bem?");
        assertThat(a).isEmpty();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-estetica";
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
