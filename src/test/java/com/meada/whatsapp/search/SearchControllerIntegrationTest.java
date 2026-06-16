package com.meada.whatsapp.search;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa a busca global (/admin/search, camada 5.22 #84) via camada HTTP. Cobre: match de
 * contato pelo nome, isolamento por empresa (dados de outra empresa NÃO aparecem), q em
 * branco → listas vazias, e sem auth → 401.
 */
class SearchControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** Provisiona contato + conversa + mensagem numa empresa e retorna o id da conversa. */
    private UUID seedConversationWithMessage(UUID companyId, String contactName,
                                             String phone, String messageContent) {
        UUID contactId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, phone, contactName);
        // conversa precisa de uma whatsapp_instance da mesma empresa (FK composta).
        UUID instanceId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) "
                + "values (?, ?, ?, ?)",
            instanceId, companyId, "inst-" + instanceId, "tok-" + instanceId);
        UUID conversationId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id) "
                + "values (?, ?, ?, ?)",
            conversationId, companyId, contactId, instanceId);
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', ?)",
            companyId, conversationId, messageContent);
        return conversationId;
    }

    @Test
    @DisplayName("GET /admin/search?q=<nome> → encontra o contato da empresa")
    void search_findsContactByName() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        seedConversationWithMessage(companyId, "Maria Silva", "+5511999990000", "oi tudo bem");
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/search?q=Maria").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contacts.length()").value(1))
            .andExpect(jsonPath("$.contacts[0].name").value("Maria Silva"))
            .andExpect(jsonPath("$.contacts[0].phoneNumber").value("+5511999990000"))
            .andExpect(jsonPath("$.conversations.length()").value(1))
            .andExpect(jsonPath("$.conversations[0].contactName").value("Maria Silva"));
    }

    @Test
    @DisplayName("GET /admin/search?q=<conteudo> → encontra a mensagem da empresa")
    void search_findsMessageByContent() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        seedConversationWithMessage(companyId, "Joao", "+5511888880000", "preciso de orcamento");
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/search?q=orcamento").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages.length()").value(1))
            .andExpect(jsonPath("$.messages[0].content").value("preciso de orcamento"))
            .andExpect(jsonPath("$.messages[0].conversationId").isNotEmpty());
    }

    @Test
    @DisplayName("GET /admin/search isola por empresa — dados de outra empresa NÃO aparecem")
    void search_tenantIsolation() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        // 2ª empresa com um contato de nome semelhante — não pode vazar.
        UUID otherCompany = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into companies (id, name, slug) values (?, ?, ?)",
            otherCompany, "Outra", "outra-" + otherCompany);
        seedConversationWithMessage(otherCompany, "Mariana Outra", "+5511777770000", "segredo");

        // a própria empresa tem a Maria — o resultado tem só a dela.
        seedConversationWithMessage(companyId, "Maria Minha", "+5511666660000", "ola");
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/search?q=Mari").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contacts.length()").value(1))
            .andExpect(jsonPath("$.contacts[0].name").value("Maria Minha"));
    }

    @Test
    @DisplayName("GET /admin/search?q= (em branco) → listas vazias")
    void search_blankQuery_returnsEmpty() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        seedConversationWithMessage(companyId, "Maria Silva", "+5511999990000", "oi");
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);

        mockMvc.perform(get("/admin/search?q=").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contacts.length()").value(0))
            .andExpect(jsonPath("$.conversations.length()").value(0))
            .andExpect(jsonPath("$.messages.length()").value(0));
    }

    @Test
    @DisplayName("GET /admin/search sem auth → 401")
    void search_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/search?q=Maria"))
            .andExpect(status().isUnauthorized());
    }
}
