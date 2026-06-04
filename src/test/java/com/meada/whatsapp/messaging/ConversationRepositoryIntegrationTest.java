package com.meada.whatsapp.messaging;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link ConversationRepository} contra PostgreSQL real
 * (Testcontainers), como service_role, com as migrations de produção.
 *
 * <p>conversations tem FKs compostas para contacts(id, company_id) e
 * whatsapp_instances(id, company_id), então o seed cria a cadeia completa
 * (company + contact + instance) por tenant antes dos testes.
 */
class ConversationRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ConversationRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONTACT_A = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CONTACT_B = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE_B = UUID.fromString("b1000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seed() {
        seedTenant(COMPANY_A, "empresa-a", INSTANCE_A, "inst-a", CONTACT_A, "+5511999990001");
        seedTenant(COMPANY_B, "empresa-b", INSTANCE_B, "inst-b", CONTACT_B, "+5511999990002");
    }

    private void seedTenant(UUID companyId, String slug, UUID instanceId, String instanceName,
                            UUID contactId, String phone) {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa " + slug, slug);
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)",
            instanceId, companyId, instanceName, "tok-" + instanceName);
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, phone, "Cliente " + slug);
    }

    private long countConversations(UUID companyId, UUID contactId, UUID instanceId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from conversations "
                + "where company_id = ? and contact_id = ? and whatsapp_instance_id = ?",
            Long.class, companyId, contactId, instanceId);
    }

    @Test
    @DisplayName("(a) primeira mensagem cria conversa aberta")
    void firstMessage_createsOpenConversation() {
        Conversation c = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);

        assertThat(c.id()).isNotNull();
        assertThat(c.companyId()).isEqualTo(COMPANY_A);
        assertThat(countConversations(COMPANY_A, CONTACT_A, INSTANCE_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("(b) segunda mensagem mesmo contato/instância retorna a mesma conversa")
    void secondMessage_returnsSameConversation() {
        Conversation first = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        Conversation second = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countConversations(COMPANY_A, CONTACT_A, INSTANCE_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("(c) conversa fechada não bloqueia: mensagem nova cria nova aberta")
    void closedConversation_createsNewOpen() {
        Conversation original = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        jdbcTemplate.update("update conversations set status = 'closed' where id = ?", original.id());

        Conversation reopened = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);

        assertThat(reopened.id()).isNotEqualTo(original.id());
        // 1 fechada (histórico) + 1 nova aberta
        assertThat(countConversations(COMPANY_A, CONTACT_A, INSTANCE_A)).isEqualTo(2);
    }

    @Test
    @DisplayName("(d) chamadas repetidas convergem para a mesma conversa aberta")
    void repeatedCalls_sameOpenConversation() {
        Conversation a = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        Conversation b = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        Conversation c = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);

        assertThat(a.id()).isEqualTo(b.id()).isEqualTo(c.id());
        assertThat(countConversations(COMPANY_A, CONTACT_A, INSTANCE_A)).isEqualTo(1);
    }

    @Test
    @DisplayName("(e) isolamento de tenant: A e B têm conversas distintas")
    void tenantIsolation_distinctConversations() {
        Conversation a = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        Conversation b = repository.resolveOpenOrCreate(COMPANY_B, CONTACT_B, INSTANCE_B);

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(a.companyId()).isEqualTo(COMPANY_A);
        assertThat(b.companyId()).isEqualTo(COMPANY_B);
    }

    // ---- markHandledByHuman -------------------------------------------------
    // Sem caso de isolamento de tenant — conversationId é PK uuid global única;
    // o WHERE id=? mira uma conversa específica, não confunde tenants.

    private String handledByOf(UUID conversationId) {
        return jdbcTemplate.queryForObject(
            "select handled_by from conversations where id = ?", String.class, conversationId);
    }

    @Test
    @DisplayName("markHandledByHuman: conversa 'ai' → flip, retorna true, estado vira 'human'")
    void mark_aiConversation_flips() {
        Conversation conv = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);  // nasce 'ai'

        boolean flipped = repository.markHandledByHuman(conv.id());

        assertThat(flipped).isTrue();
        assertThat(handledByOf(conv.id())).isEqualTo("human");
    }

    @Test
    @DisplayName("markHandledByHuman: conversa já 'human' → retorna false (idempotente), estado inalterado")
    void mark_alreadyHuman_returnsFalse() {
        Conversation conv = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        repository.markHandledByHuman(conv.id());   // 1º flip: ai → human

        boolean second = repository.markHandledByHuman(conv.id());   // redundante

        assertThat(second).isFalse();
        assertThat(handledByOf(conv.id())).isEqualTo("human");   // continua human
    }

    @Test
    @DisplayName("markHandledByHuman: conversa inexistente → retorna false")
    void mark_unknownConversation_returnsFalse() {
        boolean result = repository.markHandledByHuman(
            UUID.fromString("c9000000-0000-0000-0000-000000000099"));

        assertThat(result).isFalse();
    }

    // ---- findHandledBy ------------------------------------------------------

    @Test
    @DisplayName("findHandledBy: conversa 'ai' retorna Optional.of(\"ai\")")
    void findHandledBy_aiConversation_returnsAi() {
        Conversation conv = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);  // nasce 'ai'

        assertThat(repository.findHandledBy(conv.id())).contains("ai");
    }

    @Test
    @DisplayName("findHandledBy: conversa 'human' retorna Optional.of(\"human\")")
    void findHandledBy_humanConversation_returnsHuman() {
        Conversation conv = repository.resolveOpenOrCreate(COMPANY_A, CONTACT_A, INSTANCE_A);
        repository.markHandledByHuman(conv.id());   // ai → human

        // documenta o uso real (OutboundService compara contra "ai"); pega
        // regressão se alguém trocar por um isHandledByAi() booleano.
        assertThat(repository.findHandledBy(conv.id())).contains("human");
    }

    @Test
    @DisplayName("findHandledBy: conversa inexistente retorna empty")
    void findHandledBy_unknown_returnsEmpty() {
        assertThat(repository.findHandledBy(
            UUID.fromString("c9000000-0000-0000-0000-000000000099"))).isEmpty();
    }
}
