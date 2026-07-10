package com.meada.profiles.atelie.reminders;

import com.meada.AbstractIntegrationTest;
import com.meada.outbound.EvolutionSender;
import com.meada.profiles.atelie.proposals.AtelieProposalService;
import com.meada.profiles.atelie.proposals.ConfirmacaoProvaHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test da onda Ateliê 3 (backlog #3/#6/#7): confirmação de prova pelo cliente
 * (metadado com barreira, invalidada ao remarcar), pós-entrega com review link (toggle) e
 * reativação de inativo (opt-in OFF, cooldown = janela). EvolutionSender é um FAKE.
 */
@Import(AtelieOnda3IntegrationTest.TestConfig.class)
class AtelieOnda3IntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("f6000000-0000-0000-0000-000000000111");
    private static final UUID INSTANCE = UUID.fromString("f6100000-0000-0000-0000-000000000111");

    @Autowired
    private ConfirmacaoProvaHandler confirmacaoHandler;
    @Autowired
    private AtelieProposalService proposalService;
    @Autowired
    private AtelieReactivationJob reactivationJob;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private UUID contactId;
    private UUID conversationId;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'atelie')",
            COMPANY, "Atelie Onda3", "atelie-onda3");
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-at3", "tok-at3");
        contactId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, 'Marina')",
            contactId, COMPANY, "+5511999990371");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conversationId, COMPANY, contactId, INSTANCE);
    }

    private UUID seedProposal(String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into atelie_proposals (id, company_id, contact_id, conversation_id, customer_name, "
                + "project_type, total_cents, status) values (?, ?, ?, ?, 'Marina', 'costura', 50000, ?)",
            id, COMPANY, contactId, conversationId, status);
        return id;
    }

    @Test
    @DisplayName("confirmação de prova: barreira de contato; confirma 1x; remarcar invalida (marker <> due_date)")
    void confirmacaoProva() {
        UUID proposal = seedProposal("fechada");
        UUID fitting = jdbcTemplate.queryForObject(
            "insert into atelie_fittings (company_id, proposal_id, title, due_date, status, position) "
                + "values (?, ?, 'Primeira prova', current_date + 1, 'pendente', 0) returning id",
            UUID.class, COMPANY, proposal);

        String tag = "Confirmo!\n<confirmacao_prova>{\"fitting_id\":\"" + fitting + "\"}</confirmacao_prova>";
        // contato divergente é barrado.
        assertThat(confirmacaoHandler.parseAndConfirm(COMPANY, conversationId, UUID.randomUUID(), tag)).isFalse();
        assertThat(confirmacaoHandler.parseAndConfirm(COMPANY, conversationId, contactId, tag)).isTrue();

        record Conf(java.sql.Timestamp at, java.sql.Date due) {}
        Conf c = jdbcTemplate.queryForObject(
            "select confirmed_at, confirmed_due_date from atelie_fittings where id = ?",
            (rs, rn) -> new Conf(rs.getTimestamp("confirmed_at"), rs.getDate("confirmed_due_date")),
            fitting);
        assertThat(c.at()).isNotNull();
        assertThat(c.due().toLocalDate()).isEqualTo(LocalDate.now().plusDays(1));

        // remarcar a prova → confirmed_due_date <> due_date = confirmação invalidada (visível no painel).
        jdbcTemplate.update("update atelie_fittings set due_date = current_date + 5 where id = ?", fitting);
        Boolean stillValid = jdbcTemplate.queryForObject(
            "select confirmed_due_date = due_date from atelie_fittings where id = ?", Boolean.class, fitting);
        assertThat(stillValid).isFalse();
    }

    @Test
    @DisplayName("pós-entrega: realizada → agradecimento com review link; toggle off só notificação do funil")
    void postDelivery() {
        jdbcTemplate.update(
            "insert into atelie_config (company_id, review_link) values (?, 'https://g.page/r/atelie')",
            COMPANY);
        UUID fechada = seedProposal("fechada");
        proposalService.updateStatus(COMPANY, fechada, "realizada");

        // funil notifica realizada? (notificationText pode ser null) — o pós-entrega SEMPRE sai com toggle ON.
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .anyMatch(t -> t.contains("https://g.page/r/atelie") && t.contains("indicação"));

        jdbcTemplate.update("update atelie_config set post_delivery_enabled = false where company_id = ?",
            COMPANY);
        UUID fechada2 = seedProposal("fechada");
        fakeEvolution.reset();
        proposalService.updateStatus(COMPANY, fechada2, "realizada");
        assertThat(fakeEvolution.sent().stream().map(SentMessage::text))
            .noneMatch(t -> t.contains("indicação"));
    }

    @Test
    @DisplayName("reativação: OFF por default; ON → inativo sem proposta viva recebe 1 toque; cooldown")
    void reactivation_optIn() {
        UUID old = seedProposal("realizada");
        jdbcTemplate.update(
            "update atelie_proposals set opened_at = now() - interval '120 days' where id = ?", old);

        assertThat(reactivationJob.runReactivation()).isZero();   // default OFF.

        jdbcTemplate.update(
            "insert into atelie_config (company_id, reactivation_enabled, reactivation_days) "
                + "values (?, true, 90) on conflict (company_id) do update set "
                + "reactivation_enabled = true, reactivation_days = 90", COMPANY);
        assertThat(reactivationJob.runReactivation()).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).contains("Marina").contains("última peça");

        fakeEvolution.reset();
        assertThat(reactivationJob.runReactivation()).isZero();   // cooldown.

        // proposta viva suprime mesmo fora do cooldown.
        jdbcTemplate.update("delete from atelie_reactivation_log where company_id = ?", COMPANY);
        seedProposal("orcada");
        assertThat(reactivationJob.runReactivation()).isZero();
    }

    record SentMessage(String instanceName, String token, String number, String text) {}

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();
        void reset() { sent.clear(); }
        List<SentMessage> sent() { return sent; }
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-atelie-onda3";
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
