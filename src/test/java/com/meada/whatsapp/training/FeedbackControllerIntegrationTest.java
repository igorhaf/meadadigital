package com.meada.whatsapp.training;

import com.meada.whatsapp.admin.AbstractAdminIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o feedback de mensagens da IA (/admin/message-feedback, camada 5.25 #57) via camada HTTP.
 * Cobre: upsert cria (201) e depois atualiza (200); list só da própria empresa; sem auth → 401.
 *
 * <p>Cada teste semeia uma mensagem da IA para referenciar (company + contact + conversation +
 * message), pois o feedback tem FK para messages. Reusa a instância da empresa como portadora da
 * FK NOT NULL conversations.whatsapp_instance_id (mesmo schema do canal whatsapp).
 */
class FeedbackControllerIntegrationTest extends AbstractAdminIntegrationTest {

    private static final UUID ADMIN_SUB = UUID.fromString("44444444-4444-4444-4444-444444444444");

    /**
     * Semeia uma mensagem da IA (outbound/ai) na empresa do tenant e devolve o seu id, para o
     * feedback referenciar. Cria instância, contato, conversa e a mensagem. company_id da mensagem
     * = a empresa do tenant (isolamento). Devolve o messageId.
     */
    private UUID seedAiMessage(UUID companyId) {
        UUID instanceId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceId, companyId, "inst-" + instanceId, "tok");
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contactId, companyId, "+5511988887777", "Cliente");
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            conversationId, companyId, contactId, instanceId);
        jdbcTemplate.update(
            "insert into messages (id, company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, ?, 'outbound', 'ai', ?)",
            messageId, companyId, conversationId, "Olá! Como posso ajudar?");
        return messageId;
    }

    @Test
    @DisplayName("POST /admin/message-feedback cria (201) e re-POST atualiza (200, mesmo message_id)")
    void upsert_createsThenUpdates() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        UUID messageId = seedAiMessage(companyId);

        // 1ª vez: cria → 201
        mockMvc.perform(post("/admin/message-feedback")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"messageId\":\"" + messageId + "\",\"rating\":\"bad\","
                    + "\"correction\":\"Deveria ter oferecido agendamento.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.rating").value("bad"));

        // 2ª vez (mesmo message_id): atualiza → 200
        mockMvc.perform(post("/admin/message-feedback")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"messageId\":\"" + messageId + "\",\"rating\":\"good\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rating").value("good"));

        // Só uma linha (UNIQUE por message_id), com o rating atualizado e a correção limpa.
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from ai_message_feedback where message_id = ?", Integer.class, messageId);
        assertThatRating(messageId, "good");
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    private void assertThatRating(UUID messageId, String expected) {
        String rating = jdbcTemplate.queryForObject(
            "select rating from ai_message_feedback where message_id = ?", String.class, messageId);
        org.assertj.core.api.Assertions.assertThat(rating).isEqualTo(expected);
    }

    @Test
    @DisplayName("GET /admin/message-feedback?rating=bad lista só o feedback da própria empresa")
    void list_returnsOwnCompanyOnly() throws Exception {
        UUID companyId = seedTenantAdmin(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        String token = mintValidToken(TENANT_ADMIN_EMAIL, ADMIN_SUB);
        UUID messageId = seedAiMessage(companyId);

        mockMvc.perform(post("/admin/message-feedback")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"messageId\":\"" + messageId + "\",\"rating\":\"bad\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/admin/message-feedback?rating=bad")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].rating").value("bad"))
            .andExpect(jsonPath("$[0].messageContent").value("Olá! Como posso ajudar?"));
    }

    @Test
    @DisplayName("GET /admin/message-feedback sem auth → 401")
    void list_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/message-feedback"))
            .andExpect(status().isUnauthorized());
    }
}
