package com.meada.whatsapp.messaging;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link ContactRepository} contra PostgreSQL real
 * (Testcontainers), como service_role, com as migrations de produção.
 */
class ContactRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ContactRepository repository;

    private static final UUID COMPANY_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID COMPANY_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String PHONE = "+5511999990000";

    @BeforeEach
    void seedCompanies() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY_B, "Empresa B", "empresa-b");
    }

    private long countContacts(UUID companyId, String phone) {
        return jdbcTemplate.queryForObject(
            "select count(*) from contacts where company_id = ? and phone_number = ?",
            Long.class, companyId, phone);
    }

    @Test
    @DisplayName("(a) contato novo é criado com id gerado")
    void newContact_isCreated() {
        Contact c = repository.resolveOrCreate(COMPANY_A, PHONE, "Cliente A");

        assertThat(c.id()).isNotNull();
        assertThat(c.companyId()).isEqualTo(COMPANY_A);
        assertThat(c.phoneNumber()).isEqualTo(PHONE);
        assertThat(c.name()).isEqualTo("Cliente A");
        assertThat(countContacts(COMPANY_A, PHONE)).isEqualTo(1);
    }

    @Test
    @DisplayName("(b) contato existente é retornado, não duplica")
    void existingContact_isReturned_notDuplicated() {
        Contact first = repository.resolveOrCreate(COMPANY_A, PHONE, "Cliente A");
        Contact second = repository.resolveOrCreate(COMPANY_A, PHONE, "Cliente A");

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(countContacts(COMPANY_A, PHONE)).isEqualTo(1);
    }

    @Test
    @DisplayName("(c) chamadas repetidas convergem para o mesmo contato")
    void repeatedCalls_sameContact() {
        Contact a = repository.resolveOrCreate(COMPANY_A, PHONE, "X");
        Contact b = repository.resolveOrCreate(COMPANY_A, PHONE, "X");
        Contact c = repository.resolveOrCreate(COMPANY_A, PHONE, "X");

        assertThat(a.id()).isEqualTo(b.id()).isEqualTo(c.id());
        assertThat(countContacts(COMPANY_A, PHONE)).isEqualTo(1);
    }

    @Test
    @DisplayName("(d) soft-deleted não atrapalha: recria com id novo")
    void softDeleted_recreatesWithNewId() {
        Contact original = repository.resolveOrCreate(COMPANY_A, PHONE, "Antigo");
        jdbcTemplate.update("update contacts set deleted_at = now() where id = ?", original.id());

        Contact recreated = repository.resolveOrCreate(COMPANY_A, PHONE, "Novo");

        assertThat(recreated.id()).isNotEqualTo(original.id());
        assertThat(recreated.name()).isEqualTo("Novo");
        // o soft-deleted continua lá (1 deletado + 1 ativo)
        assertThat(countContacts(COMPANY_A, PHONE)).isEqualTo(2);
    }

    @Test
    @DisplayName("(e) isolamento de tenant: mesmo phone em A e B são contatos distintos")
    void tenantIsolation_distinctContacts() {
        Contact a = repository.resolveOrCreate(COMPANY_A, PHONE, "Em A");
        Contact b = repository.resolveOrCreate(COMPANY_B, PHONE, "Em B");

        assertThat(a.id()).isNotEqualTo(b.id());
        assertThat(a.companyId()).isEqualTo(COMPANY_A);
        assertThat(b.companyId()).isEqualTo(COMPANY_B);
    }

    @Test
    @DisplayName("(f) name null é preenchido por chamada posterior com name")
    void nullName_isFilledLater() {
        Contact created = repository.resolveOrCreate(COMPANY_A, PHONE, null);
        assertThat(created.name()).isNull();

        Contact filled = repository.resolveOrCreate(COMPANY_A, PHONE, "Fulano");

        assertThat(filled.id()).isEqualTo(created.id());
        assertThat(filled.name()).isEqualTo("Fulano");
    }

    @Test
    @DisplayName("(g) name existente não é sobrescrito (caminho quente: SELECT acha e retorna)")
    void existingName_notOverwritten() {
        Contact created = repository.resolveOrCreate(COMPANY_A, PHONE, "Original");

        Contact again = repository.resolveOrCreate(COMPANY_A, PHONE, "Novo");

        assertThat(again.id()).isEqualTo(created.id());
        assertThat(again.name()).isEqualTo("Original");
    }

    // ---- findPhoneByConversationId ------------------------------------------
    // Nota: sem caso de "isolamento de tenant" — conversationId é PK uuid global
    // única; o JOIN resolve o contato dono daquela conversa específica. Buscar por
    // PK não confunde tenants. Só 2 casos: existe / inexistente.

    private static final UUID INSTANCE_A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CONTACT_A = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID CONV_A = UUID.fromString("a3000000-0000-0000-0000-000000000001");

    /** Semeia a cadeia FK (instance + contact + conversation) para o JOIN. */
    private void seedConversation() {
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE_A, COMPANY_A, "inst-a", "tok-a");
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            CONTACT_A, COMPANY_A, PHONE, "Cliente A");
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            CONV_A, COMPANY_A, CONTACT_A, INSTANCE_A);
    }

    @Test
    @DisplayName("findPhoneByConversationId: conversa existente retorna o phone do contato")
    void findPhone_existing_returnsPhone() {
        seedConversation();

        assertThat(repository.findPhoneByConversationId(CONV_A)).contains(PHONE);
    }

    @Test
    @DisplayName("findPhoneByConversationId: conversa inexistente retorna empty")
    void findPhone_unknown_returnsEmpty() {
        assertThat(repository.findPhoneByConversationId(CONV_A)).isEmpty();   // nada semeado
    }
}
