package com.meada.whatsapp.webchat;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.ai.AiProvider;
import com.meada.whatsapp.ai.AiResponse;
import com.meada.whatsapp.ai.Prompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o endpoint público do widget de chat web (/api/chat/{slug}, camada 5.25 #73) via camada
 * HTTP. Cobre: POST cria conversa web (channel='web') + inbound + outbound, devolvendo o reply do
 * fake de IA; slug desconhecido → 404.
 *
 * <p>A IA é um FAKE {@code @Primary} ({@link TestConfig}) — o GeminiProvider real chamaria a API
 * externa, o que NÃO queremos nos testes. Mesmo padrão do OutboundServiceIntegrationTest. Como é um
 * endpoint PÚBLICO (fora de /admin/), não há token: o JwtAuthenticationFilter nem filtra /api/chat.
 */
@Import(WebChatControllerIntegrationTest.TestConfig.class)
class WebChatControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String CANNED_REPLY = "Olá! Sou a IA da empresa. Como posso ajudar?";

    /** Semeia uma empresa ATIVA + uma whatsapp_instance (portadora da FK) + ai_settings mínimo,
     *  para o widget abrir conversa e o PromptBuilder montar o prompt sem erro. Devolve o slug. */
    private String seedCompany() {
        UUID companyId = UUID.randomUUID();
        UUID instanceId = UUID.randomUUID();
        String slug = "empresa-web-" + companyId;
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            companyId, "Empresa Web", slug);
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instanceId, companyId, "inst-" + instanceId, "tok");
        jdbcTemplate.update(
            "insert into ai_settings (company_id, tone, model_provider) values (?, 'Cordial.', 'gemini')",
            companyId);
        return slug;
    }

    @Test
    @DisplayName("POST /api/chat/{slug} → 200 com reply do fake; cria conversa web + inbound + outbound")
    void chat_createsWebConversationAndMessages() throws Exception {
        String slug = seedCompany();

        mockMvc.perform(post("/api/chat/" + slug)
                .contentType("application/json")
                .content("{\"sessionId\":\"sess-abc\",\"message\":\"Oi, vocês atendem hoje?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reply").value(CANNED_REPLY));

        // Uma conversa web foi criada.
        Integer webConversations = jdbcTemplate.queryForObject(
            "select count(*) from conversations where channel = 'web'", Integer.class);
        assertThat(webConversations).isEqualTo(1);

        // Inbound do visitante + outbound da IA persistidos.
        Integer inbound = jdbcTemplate.queryForObject(
            "select count(*) from messages where direction = 'inbound' and content = ?",
            Integer.class, "Oi, vocês atendem hoje?");
        assertThat(inbound).isEqualTo(1);
        Integer outbound = jdbcTemplate.queryForObject(
            "select count(*) from messages where direction = 'outbound' and sender = 'ai' and content = ?",
            Integer.class, CANNED_REPLY);
        assertThat(outbound).isEqualTo(1);

        // O contato web foi criado com phone sintético e channels agregando o canal web.
        Integer webContact = jdbcTemplate.queryForObject(
            "select count(*) from contacts where phone_number = ? and channels ->> 'web' = ?",
            Integer.class, "web:sess-abc", "sess-abc");
        assertThat(webContact).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/chat/{slug} com slug desconhecido → 404 company_not_found")
    void chat_unknownSlug_returns404() throws Exception {
        mockMvc.perform(post("/api/chat/slug-inexistente")
                .contentType("application/json")
                .content("{\"sessionId\":\"sess-x\",\"message\":\"oi\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.reason").value("company_not_found"));
    }

    /** Fake @Primary do AiProvider: devolve sempre a mesma resposta, sem chamar a API externa. */
    static class FakeAiProvider implements AiProvider {
        @Override
        public AiResponse generate(Prompt prompt) {
            return new AiResponse(CANNED_REPLY, false, null, 5, 5, 1L);
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeAiProvider fakeAiProvider() {
            return new FakeAiProvider();
        }
    }
}
