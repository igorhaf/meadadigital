package com.meada.whatsapp.profiles.legal.cases;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.outbound.EvolutionSender;
import com.meada.whatsapp.profiles.legal.LegalCnjValidator;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.DuplicateCnjException;
import com.meada.whatsapp.profiles.legal.cases.LegalCaseService.InvalidCnjException;
import com.meada.whatsapp.profiles.legal.clients.LegalClient;
import com.meada.whatsapp.profiles.legal.clients.LegalClientService;
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
 * Testa o LegalCaseService (camada 7.2): create válido, CNJ inválido → 400, duplicate CNJ → 409,
 * updateStatus dispara notifier (cliente vinculado a contato).
 */
@Import(LegalCaseServiceTest.TestConfig.class)
class LegalCaseServiceTest extends AbstractIntegrationTest {

    @Autowired
    private LegalCaseService service;
    @Autowired
    private LegalClientService clientService;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("c9000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("d9000000-0000-0000-0000-000000000001");

    private String validCnj;
    private UUID clientId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'legal')",
            COMPANY, "Adv O", "adv-o");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@adv-o.dev', 'admin')",
            USER, COMPANY);
        // contato + cliente vinculado (para a notificação ter canal).
        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511977776666", "Cliente Adv");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by, last_message_at) "
            + "values (?, ?, ?, ?, 'open', 'ai', now())", conv, COMPANY, contact, instance);
        LegalClient c = clientService.create(COMPANY, USER, "Cliente Adv", null, null, null, contact, null);
        clientId = c.id();
        validCnj = buildValidCnj("0710233", "2025", "8", "07", "0019");
    }

    private String buildValidCnj(String seq, String ano, String j, String tr, String ori) {
        String dv = LegalCnjValidator.computeCheckDigits(seq + ano + j + tr + ori);
        return seq + dv + ano + j + tr + ori;
    }

    @Test
    @DisplayName("create válido → persiste com CNJ normalizado + audita")
    void create_valid() {
        LegalCase c = service.create(COMPANY, USER, clientId, LegalCnjValidator.format(validCnj),
            "Ação Trabalhista", "desc", "1ª Vara", "Fórum Central", "Trabalhista");
        assertThat(c.cnjNumber()).isEqualTo(validCnj);
        assertThat(c.cnjNumberFormatted()).contains("-");
        assertThat(c.legalClientName()).isEqualTo("Cliente Adv");
    }

    @Test
    @DisplayName("create com CNJ inválido → InvalidCnjException")
    void create_invalidCnj() {
        assertThatThrownBy(() -> service.create(COMPANY, USER, clientId, "00000000000000000000",
            "X", null, null, null, null))
            .isInstanceOf(InvalidCnjException.class);
    }

    @Test
    @DisplayName("create com CNJ duplicado → DuplicateCnjException")
    void create_duplicateCnj() {
        service.create(COMPANY, USER, clientId, validCnj, "Ação 1", null, null, null, null);
        assertThatThrownBy(() -> service.create(COMPANY, USER, clientId, validCnj, "Ação 2", null, null, null, null))
            .isInstanceOf(DuplicateCnjException.class);
    }

    @Test
    @DisplayName("updateStatus ativo→suspenso dispara notificação outbound")
    void updateStatus_notifies() {
        LegalCase c = service.create(COMPANY, USER, clientId, validCnj, "Ação", null, null, null, null);
        assertThat(c.status()).isEqualTo("ativo");

        LegalCase up = service.updateStatus(COMPANY, USER, c.id(), "suspenso");
        assertThat(up.status()).isEqualTo("suspenso");
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("SUSPENSÃO");
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-legal";
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean @Primary
        FakeEvolutionSender fakeEvolutionSender() { return new FakeEvolutionSender(); }
    }
}
