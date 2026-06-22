package com.meada.whatsapp.ai;

import com.meada.whatsapp.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link PromptBuilder} com os 4 repos de leitura reais +
 * MessageRepository, contra PostgreSQL real (Testcontainers).
 */
class PromptBuilderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PromptBuilder promptBuilder;

    private static final UUID COMPANY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONTACT = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CONV = UUID.fromString("a3000000-0000-0000-0000-000000000001");

    @BeforeEach
    void seedBase() {
        jdbcTemplate.update("insert into companies (id, name, slug) values (?, ?, ?)",
            COMPANY, "Empresa A", "empresa-a");
        jdbcTemplate.update(
            "insert into whatsapp_instances (id, company_id, instance_name, evolution_token) values (?, ?, ?, ?)",
            INSTANCE, COMPANY, "inst-a", "tok-a");
        jdbcTemplate.update(
            "insert into contacts (id, company_id, phone_number, name) values (?, ?, ?, ?)",
            CONTACT, COMPANY, "+5511999990001", "Cliente A");
        jdbcTemplate.update(
            "insert into conversations (id, company_id, contact_id, whatsapp_instance_id, status, handled_by) "
                + "values (?, ?, ?, ?, 'open', 'ai')",
            CONV, COMPANY, CONTACT, INSTANCE);
    }

    @Test
    @DisplayName("AiSettings configurado + dados: systemPrompt contém tone, regras, serviços, FAQs, horários")
    void configured_withData() {
        jdbcTemplate.update(
            "insert into ai_settings (company_id, tone, system_rules, restrictions, handoff_triggers, model_provider) "
                + "values (?, 'Tom descontraído.', 'Use emojis.', 'Não fale de política.', 'Cliente irritado pede humano.', 'gemini')",
            COMPANY);
        jdbcTemplate.update("insert into services (company_id, name, description, price_cents, active) "
            + "values (?, 'Corte', 'Corte masculino', 5000, true)", COMPANY);
        jdbcTemplate.update("insert into services (company_id, name, description, price_cents, active) "
            + "values (?, 'Consulta', 'Avaliação gratuita', null, true)", COMPANY);
        jdbcTemplate.update("insert into services (company_id, name, description, price_cents, active) "
            + "values (?, 'Brinde', 'Amostra grátis', 0, true)", COMPANY);   // preço ZERO explícito (≠ null)
        jdbcTemplate.update("insert into faqs (company_id, question, answer, active) "
            + "values (?, 'Aceitam cartão?', 'Sim, todos.', true)", COMPANY);
        jdbcTemplate.update("insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
            + "values (?, 1, false, '09:00'::time, '12:00'::time)", COMPANY);
        jdbcTemplate.update("insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
            + "values (?, 1, false, '14:00'::time, '18:00'::time)", COMPANY);
        jdbcTemplate.update("insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
            + "values (?, 0, true, null, null)", COMPANY);

        Prompt prompt = promptBuilder.build(COMPANY, CONV, "Oi");
        String sys = prompt.systemPrompt();

        assertThat(sys).contains("Tom descontraído.");
        assertThat(sys).contains("# Regras adicionais").contains("Use emojis.");
        assertThat(sys).contains("# Restrições").contains("Não fale de política.");
        assertThat(sys).contains("Cliente irritado pede humano.");   // handoff customizado
        assertThat(sys).contains("# Serviços oferecidos");
        assertThat(sys).contains("- Corte: Corte masculino — R$ 50,00");   // com preço
        assertThat(sys).contains("- Consulta: Avaliação gratuita");         // sem preço (null), sem "R$"
        assertThat(sys).doesNotContain("Consulta: Avaliação gratuita — R$");
        assertThat(sys).contains("- Brinde: Amostra grátis — R$ 0,00");     // preço ZERO ≠ null → formata
        assertThat(sys).contains("# Perguntas frequentes");
        assertThat(sys).contains("P: Aceitam cartão?").contains("R: Sim, todos.");
        assertThat(sys).contains("# Horários de atendimento");
        assertThat(sys).contains("Segunda: 09:00-12:00, 14:00-18:00");   // múltiplas janelas
        assertThat(sys).contains("Domingo: fechado");

        assertThat(prompt.userMessage()).isEqualTo("Oi");
        assertThat(prompt.history()).isEmpty();   // conversa sem mensagens anteriores
    }

    @Test
    @DisplayName("AiSettings ausente: usa defaults neutros (tone e handoff default)")
    void aiSettingsAbsent_usesDefaults() {
        // nenhuma linha de ai_settings; nenhum service/faq/hora
        Prompt prompt = promptBuilder.build(COMPANY, CONV, "Oi");
        String sys = prompt.systemPrompt();

        assertThat(sys).contains("Cordial e profissional.");                       // DEFAULT_TONE
        assertThat(sys).contains("falar com um atendente humano");                 // DEFAULT_HANDOFF
        // seções opcionais sem dados → cabeçalhos não aparecem
        assertThat(sys).doesNotContain("# Regras adicionais");
        assertThat(sys).doesNotContain("# Restrições");
        assertThat(sys).doesNotContain("# Serviços oferecidos");
        assertThat(sys).doesNotContain("# Perguntas frequentes");
        assertThat(sys).doesNotContain("# Horários de atendimento");
    }

    @Test
    @DisplayName("tenant vazio: seções opcionais omitidas, mas estrutura fixa presente")
    void emptyTenant_optionalSectionsOmitted() {
        Prompt prompt = promptBuilder.build(COMPANY, CONV, "Olá");
        String sys = prompt.systemPrompt();

        // partes fixas sempre presentes
        assertThat(sys).contains("# Tom");
        assertThat(sys).contains("# Como você deve agir");
        assertThat(sys).contains("# Quando transferir para um atendente humano");
        assertThat(sys).contains("# Formato da sua resposta");
        // nenhum cabeçalho órfão de seção opcional vazia
        assertThat(sys).doesNotContain("# Serviços oferecidos");
    }

    @Test
    @DisplayName("histórico truncado ao limite (20): conversa com 25 mensagens → 20 turns")
    void historyTruncatedToLimit() {
        for (int i = 1; i <= 25; i++) {
            jdbcTemplate.update(
                "insert into messages (company_id, conversation_id, direction, sender, content, created_at) "
                    + "values (?, ?, 'inbound', 'contact', ?, now() + (? || ' seconds')::interval)",
                COMPANY, CONV, "msg" + i, i);
        }

        Prompt prompt = promptBuilder.build(COMPANY, CONV, "atual");

        assertThat(prompt.history()).hasSize(20);
        // as 20 mais recentes, cronológicas: msg6 .. msg25
        assertThat(prompt.history().get(0).text()).isEqualTo("msg6");
        assertThat(prompt.history().get(19).text()).isEqualTo("msg25");
    }

    // ---- persona por perfil (camada 7.0) ------------------------------------

    @Test
    @DisplayName("perfil legal: systemPrompt traz a persona Legal (advocacia) ANTES do base")
    void profileLegal_injectsPersona() {
        jdbcTemplate.update("update companies set profile_id = 'legal' where id = ?", COMPANY);
        String sys = promptBuilder.build(COMPANY, CONV, "Oi").systemPrompt();
        assertThat(sys).contains("Persona (Legal)");
        assertThat(sys).contains("escritório de advocacia");
        assertThat(sys).contains("consultar o advogado responsável");
        // persona vem ANTES do prompt base (o template começa com "# Tom").
        assertThat(sys.indexOf("Persona (Legal)")).isLessThan(sys.indexOf("# Tom"));
    }

    @Test
    @DisplayName("perfil dental: systemPrompt traz a persona Dental (odonto)")
    void profileDental_injectsPersona() {
        jdbcTemplate.update("update companies set profile_id = 'dental' where id = ?", COMPANY);
        String sys = promptBuilder.build(COMPANY, CONV, "Oi").systemPrompt();
        assertThat(sys).contains("Persona (Dental)");
        assertThat(sys).contains("clínica odontológica");
        assertThat(sys).contains("NUNCA dê diagnóstico");
    }

    @Test
    @DisplayName("perfil sushi: systemPrompt traz a persona Sushi (restaurante)")
    void profileSushi_injectsPersona() {
        jdbcTemplate.update("update companies set profile_id = 'sushi' where id = ?", COMPANY);
        String sys = promptBuilder.build(COMPANY, CONV, "Oi").systemPrompt();
        assertThat(sys).contains("Persona (Sushi)");
        assertThat(sys).contains("restaurante de sushi");
        assertThat(sys).contains("endereço de entrega");
    }

    @Test
    @DisplayName("perfil generic (default): NENHUMA persona injetada — só o prompt base")
    void profileGeneric_noPersona() {
        // COMPANY do seed já é 'generic' (default da coluna). Sem cabeçalho de persona.
        String sys = promptBuilder.build(COMPANY, CONV, "Oi").systemPrompt();
        assertThat(sys).doesNotContain("# Persona");
    }
}
