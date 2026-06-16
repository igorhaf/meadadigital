package com.meada.whatsapp.engagement;

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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link ReactivationJob} (camada 5.21 #81) contra PostgreSQL real
 * (Testcontainers). A lógica é exercitada via {@link ReactivationJob#runReactivation()}
 * direto (sem depender do scheduler). O EvolutionSender é um FAKE que só registra os envios.
 *
 * <p>Cenários: contato inativo elegível é reativado (envio + reactivated_at marcado e disparo
 * único na 2ª passada); contato ativo recente NÃO é elegível; empresa sem config de reativação
 * é ignorada; contato bloqueado é excluído.
 */
@Import(ReactivationJobIntegrationTest.TestConfig.class)
class ReactivationJobIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID INSTANCE = UUID.fromString("c1000000-0000-0000-0000-000000000001");

    @Autowired
    private ReactivationJob job;
    @Autowired
    private ReactivationRepository repository;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    @BeforeEach
    void seed() {
        fakeEvolution.reset();
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY, "Empresa C", "empresa-c");
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)", INSTANCE, COMPANY, "inst-c", "tok-c");
        // Config de reativação: 7 dias + mensagem.
        jdbcTemplate.update(
            "insert into ai_settings (company_id, model_provider, reactivation_days, reactivation_message) "
                + "values (?, 'gemini', 7, ?)", COMPANY, "Sentimos sua falta! Volte a conversar.");
    }

    /** Cria um contato com uma conversa e uma mensagem inbound num instante dado (last_activity). */
    private UUID seedContactWithActivity(UUID contactId, UUID convId, String phone,
                                         boolean blocked, Instant lastActivity) {
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name, blocked) values (?, ?, ?, ?, ?)",
            contactId, COMPANY, phone, "Cliente", blocked);
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            convId, COMPANY, contactId, INSTANCE);
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content, created_at) "
                + "values (?, ?, 'inbound', 'contact', ?, ?)",
            COMPANY, convId, "Oi", Timestamp.from(lastActivity));
        return contactId;
    }

    private Instant reactivatedAtOf(UUID contactId) {
        Timestamp ts = jdbcTemplate.queryForObject(
            "select reactivated_at from contacts where id = ?", Timestamp.class, contactId);
        return ts == null ? null : ts.toInstant();
    }

    @Test
    @DisplayName("contato inativo há > reactivation_days → findDue o retorna; runReactivation envia e marca reactivated_at")
    void inactiveContact_isReactivated() {
        UUID contact = UUID.fromString("c2000000-0000-0000-0000-000000000001");
        UUID conv = UUID.fromString("c3000000-0000-0000-0000-000000000001");
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);   // bem além dos 7 dias
        seedContactWithActivity(contact, conv, "+5511970000001", false, old);

        // A query de elegibilidade retorna o contato.
        List<DueContact> due = repository.findDue(COMPANY, 7);
        assertThat(due).extracting(DueContact::contactId).containsExactly(contact);

        int marked = job.runReactivation();

        assertThat(marked).isEqualTo(1);
        assertThat(fakeEvolution.sent()).hasSize(1);
        assertThat(fakeEvolution.sent().get(0).text()).isEqualTo("Sentimos sua falta! Volte a conversar.");
        assertThat(reactivatedAtOf(contact)).isNotNull();

        // Disparo único: rodar de novo NÃO reativa (reactivated_at >= last_activity).
        fakeEvolution.reset();
        int markedAgain = job.runReactivation();
        assertThat(markedAgain).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
    }

    @Test
    @DisplayName("contato com atividade recente (< reactivation_days) → NÃO elegível, não reativa")
    void recentContact_notReactivated() {
        UUID contact = UUID.fromString("c2000000-0000-0000-0000-000000000002");
        UUID conv = UUID.fromString("c3000000-0000-0000-0000-000000000002");
        Instant recent = Instant.now().minus(1, ChronoUnit.DAYS);   // dentro dos 7 dias
        seedContactWithActivity(contact, conv, "+5511970000002", false, recent);

        assertThat(repository.findDue(COMPANY, 7)).isEmpty();

        int marked = job.runReactivation();

        assertThat(marked).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
        assertThat(reactivatedAtOf(contact)).isNull();
    }

    @Test
    @DisplayName("contato bloqueado, mesmo inativo → excluído da reativação")
    void blockedContact_excluded() {
        UUID contact = UUID.fromString("c2000000-0000-0000-0000-000000000003");
        UUID conv = UUID.fromString("c3000000-0000-0000-0000-000000000003");
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        seedContactWithActivity(contact, conv, "+5511970000003", true, old);   // blocked

        assertThat(repository.findDue(COMPANY, 7)).isEmpty();
        assertThat(job.runReactivation()).isZero();
        assertThat(reactivatedAtOf(contact)).isNull();
    }

    @Test
    @DisplayName("empresa sem config de reativação → job ignora (nenhum contato reativado)")
    void companyWithoutConfig_ignored() {
        // Remove a config de reativação da empresa (deixa só o ai_settings sem os campos).
        jdbcTemplate.update(
            "update ai_settings set reactivation_days = null, reactivation_message = null "
                + "where company_id = ?", COMPANY);
        UUID contact = UUID.fromString("c2000000-0000-0000-0000-000000000004");
        UUID conv = UUID.fromString("c3000000-0000-0000-0000-000000000004");
        Instant old = Instant.now().minus(30, ChronoUnit.DAYS);
        seedContactWithActivity(contact, conv, "+5511970000004", false, old);

        int marked = job.runReactivation();

        assertThat(marked).isZero();
        assertThat(fakeEvolution.sent()).isEmpty();
        assertThat(reactivatedAtOf(contact)).isNull();
    }

    // ---- fake ---------------------------------------------------------------

    /** Registra cada envio (instanceName, token, number, text) sem fazer HTTP. */
    record SentMessage(String instanceName, String token, String number, String text) {
    }

    static class FakeEvolutionSender implements EvolutionSender {
        private final List<SentMessage> sent = new CopyOnWriteArrayList<>();

        void reset() {
            sent.clear();
        }

        List<SentMessage> sent() {
            return sent;
        }

        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            sent.add(new SentMessage(instanceName, token, number, text));
            return "key-reactivation";
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
