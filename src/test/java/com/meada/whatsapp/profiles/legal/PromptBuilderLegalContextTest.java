package com.meada.whatsapp.profiles.legal;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.ai.Prompt;
import com.meada.whatsapp.ai.PromptBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa que o PromptBuilder injeta o contexto de processos do cliente legal identificado pela
 * conversa (camada 7.2), e o bloco de "não identificado" quando o contato não tem cliente.
 */
class PromptBuilderLegalContextTest extends AbstractIntegrationTest {

    @Autowired
    private PromptBuilder promptBuilder;

    private static final UUID COMPANY = UUID.fromString("cb000000-0000-0000-0000-000000000001");

    /** Semeia uma empresa legal + conversa; opcionalmente vincula um cliente com 1 processo. */
    private UUID seedConversation(boolean withClient) {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'legal') "
            + "on conflict (id) do nothing", COMPANY, "Adv Prompt", "adv-prompt");
        // ai_settings mínimo p/ o PromptBuilder montar.
        jdbcTemplate.update("insert into ai_settings (company_id, tone, system_rules, model_provider) "
            + "values (?, 'Formal.', 'Seja claro.', 'gemini') on conflict do nothing", COMPANY);
        UUID instance = UUID.randomUUID();
        UUID contact = UUID.randomUUID();
        UUID conv = UUID.randomUUID();
        jdbcTemplate.update("insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            instance, COMPANY, "inst", "tok");
        jdbcTemplate.update("insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            contact, COMPANY, "+5511955554444", "Telefone Cliente");
        jdbcTemplate.update("insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, ?, 'open', 'ai')", conv, COMPANY, contact, instance);
        if (withClient) {
            UUID clientId = jdbcTemplate.queryForObject(
                "insert into legal_clients (company_id, name, contact_id) values (?, 'Joana Réu', ?) returning id",
                UUID.class, COMPANY, contact);
            jdbcTemplate.update("insert into legal_cases (company_id, legal_client_id, cnj_number, title, status) "
                + "values (?, ?, '07102331520258070019', 'Ação Trabalhista vs ACME', 'ativo')", COMPANY, clientId);
        }
        return conv;
    }

    @Test
    @DisplayName("cliente identificado por telefone → prompt traz o processo dele")
    void identifiedClient_injectsCases() {
        UUID conv = seedConversation(true);
        Prompt prompt = promptBuilder.build(COMPANY, conv, "Como está meu processo?");
        String sys = prompt.systemPrompt();
        assertThat(sys).contains("Persona (Legal)");
        assertThat(sys).contains("DADOS DO CLIENTE");
        assertThat(sys).contains("Joana Réu");
        assertThat(sys).contains("Ação Trabalhista vs ACME");
    }

    @Test
    @DisplayName("telefone não identificado → prompt pede identificação, sem processos")
    void unidentified_asksToIdentify() {
        UUID conv = seedConversation(false);
        String sys = promptBuilder.build(COMPANY, conv, "Oi").systemPrompt();
        assertThat(sys).contains("Persona (Legal)");
        assertThat(sys).contains("CLIENTE NÃO IDENTIFICADO");
    }
}
