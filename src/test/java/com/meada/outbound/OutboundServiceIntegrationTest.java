package com.meada.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.AbstractIntegrationTest;
import com.meada.ai.AiException;
import com.meada.ai.AiInsights;
import com.meada.ai.AiProvider;
import com.meada.ai.AiResponse;
import com.meada.ai.AiTransientException;
import com.meada.ai.AppointmentAction;
import com.meada.ai.DetectedIntent;
import com.meada.ai.Prompt;
import com.meada.ai.SchedulingIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test do {@link OutboundService} — cobre a matriz de fluxo da Fase 3.3
 * (9 casos + pré-condição) contra PostgreSQL real (Testcontainers).
 *
 * <p>IA e Evolution são FAKES controláveis (filas de respostas/exceções) via
 * {@link TestConfig} {@code @Primary}; todo o resto (repos, PromptBuilder, RetryRunner)
 * é real. O retry roda de verdade, mas com backoff de 1ms (ver {@link #retryProps}) —
 * os testes de exaustão (casos 4 e 7) exercitam as 3 tentativas em ~2ms.
 *
 * <p>O caso 9.1 (phone ausente) NÃO tem teste: é inalcançável por dados reais
 * (phone_number NOT NULL, FK ON DELETE RESTRICT) — o guard no código é defesa contra
 * correção manual de dados (ver javadoc no OutboundService.sendAndPersist).
 */
@Import(OutboundServiceIntegrationTest.TestConfig.class)
class OutboundServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private OutboundService service;
    @Autowired
    private FakeAiProvider fakeAi;
    @Autowired
    private FakeEvolutionSender fakeEvolution;

    private static final UUID COMPANY = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID CONTACT = UUID.fromString("a2000000-0000-0000-0000-000000000001");
    private static final UUID INSTANCE = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID CONV = UUID.fromString("a3000000-0000-0000-0000-000000000001");

    /** backoff curto: 3 tentativas, esperas de 1ms — exaustão testada sem latência. */
    @DynamicPropertySource
    static void retryProps(DynamicPropertyRegistry registry) {
        registry.add("outbound.retry.max-attempts", () -> "3");
        registry.add("outbound.retry.backoff-ms[0]", () -> "1");
        registry.add("outbound.retry.backoff-ms[1]", () -> "1");
    }

    @BeforeEach
    void seed() {
        fakeAi.reset();
        fakeEvolution.reset();

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
        // config mínima para o PromptBuilder real montar o prompt sem erro.
        jdbcTemplate.update(
            "insert into ai_settings (company_id, tone, system_rules, model_provider) "
                + "values (?, 'Cordial.', 'Seja breve.', 'gemini')", COMPANY);
        jdbcTemplate.update("insert into services (company_id, name, description, price_cents, active) "
            + "values (?, 'Corte', 'Corte masculino', 5000, true)", COMPANY);
        jdbcTemplate.update("insert into faqs (company_id, question, answer, active) "
            + "values (?, 'Aceitam cartão?', 'Sim.', true)", COMPANY);
        // business_hours DINÂMICO (camada 5.4): janela clampada a agora±1h no fuso SP, no
        // weekday atual, pra os testes da matriz (3.3) NUNCA caírem fora do horário e
        // gerarem flakiness quando o CI rodar fora de seg 9-18h. O gate de horário do
        // OutboundService agora intercepta todos eles; sem esta janela larga, um run de
        // sábado/madrugada cortaria para PROCESSED_OUTSIDE_HOURS e quebraria a matriz.
        seedOpenWindowAroundNow();
    }

    /** Insere uma janela business_hours [agora-1h, agora+1h] clampada aos limites do dia,
     *  no weekday atual (fuso SP). Garante que o gate de horário deixa passar. */
    private void seedOpenWindowAroundNow() {
        LocalDateTime nowSp = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        int weekday = nowSp.getDayOfWeek().getValue() % 7;   // ISO Mon=1..Sun=7 → Sun=0..Sat=6
        LocalTime now = nowSp.toLocalTime();
        // clamp NÃO-circular: minusHours/plusHours em LocalTime dão a volta (23:30→00:30);
        // comparamos contra os limites do dia explicitamente para a janela conter 'now'.
        LocalTime opens = now.isAfter(LocalTime.of(1, 0)) ? now.minusHours(1) : LocalTime.MIDNIGHT;
        LocalTime closes = now.isBefore(LocalTime.of(22, 59)) ? now.plusHours(1) : LocalTime.of(23, 59, 59);
        jdbcTemplate.update("insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
            + "values (?, ?, false, ?::time, ?::time)", COMPANY, weekday, opens.toString(), closes.toString());
    }

    /** weekday atual (fuso SP) — para os testes do gate seedarem o dia certo. */
    private static int currentWeekdaySp() {
        return LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).getDayOfWeek().getValue() % 7;
    }

    // ---- helpers ------------------------------------------------------------

    private MessageInboundProcessedEvent event() {
        return new MessageInboundProcessedEvent(COMPANY, CONV, INSTANCE, "Oi, tudo bem?");
    }

    private MessageInboundProcessedEvent eventWith(UUID conversationId, UUID instanceId) {
        return new MessageInboundProcessedEvent(COMPANY, conversationId, instanceId, "Oi, tudo bem?");
    }

    private AiResponse aiReply(String reply, boolean needsHuman) {
        return new AiResponse(reply, needsHuman, null, 10, 5, 100L);
    }

    private String handledByOf(UUID conversationId) {
        return jdbcTemplate.queryForObject(
            "select handled_by from conversations where id = ?", String.class, conversationId);
    }

    private long countOutbound(UUID conversationId) {
        return jdbcTemplate.queryForObject(
            "select count(*) from messages where conversation_id = ? and direction = 'outbound'",
            Long.class, conversationId);
    }

    private Map<String, Object> outboundRow(UUID conversationId) {
        return jdbcTemplate.queryForMap(
            "select direction, sender, content, evolution_message_id from messages "
                + "where conversation_id = ? and direction = 'outbound'", conversationId);
    }

    // ---- pré-condição (Bloco 0) ---------------------------------------------

    @Test
    @DisplayName("pré: conversa já 'human' → SKIPPED_NOT_AI, IA não chamada, nada muda")
    void skipped_whenConversationHandledByHuman() {
        jdbcTemplate.update("update conversations set handled_by = 'human' where id = ?", CONV);

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.SKIPPED_NOT_AI);
        assertThat(fakeAi.calls()).isZero();
        assertThat(countOutbound(CONV)).isZero();
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    @Test
    @DisplayName("pré: conversa inexistente → SKIPPED_NOT_AI, IA não chamada")
    void skipped_whenConversationAbsent() {
        UUID unknown = UUID.fromString("a3000000-0000-0000-0000-0000000000ff");

        OutboundOutcome outcome = service.process(eventWith(unknown, INSTANCE));

        assertThat(outcome).isEqualTo(OutboundOutcome.SKIPPED_NOT_AI);
        assertThat(fakeAi.calls()).isZero();
    }

    // ---- casos 1-3 (branching do AiResponse) --------------------------------

    @Test
    @DisplayName("caso 1: needsHuman + reply → envia ponte, persiste, flipa → FLIPPED_AI_HANDOFF")
    void flippedHandoff_whenNeedsHumanWithReply() {
        fakeAi.enqueue(aiReply("Já te conecto com um atendente.", true));
        fakeEvolution.enqueue("key-1");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_HANDOFF);
        assertThat(fakeEvolution.calls()).isEqualTo(1);
        assertThat(countOutbound(CONV)).isEqualTo(1);
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    @Test
    @DisplayName("caso 2: needsHuman sem reply → flipa direto, não envia → FLIPPED_AI_HANDOFF")
    void flippedHandoff_whenNeedsHumanNoReply() {
        fakeAi.enqueue(aiReply(null, true));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_HANDOFF);
        assertThat(fakeEvolution.calls()).isZero();
        assertThat(countOutbound(CONV)).isZero();
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    @Test
    @DisplayName("caso 3: ¬needsHuman + reply em branco (contrato quebrado) → FLIPPED_AI_BAD_REPLY")
    void flippedBadReply_whenNotNeedsHumanEmptyReply() {
        fakeAi.enqueue(aiReply("   ", false));   // branco = sem resposta efetiva

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_BAD_REPLY);
        assertThat(fakeEvolution.calls()).isZero();
        assertThat(countOutbound(CONV)).isZero();
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    // ---- rede de segurança: tag de ação que sobrou sem interpretação ---------

    @Test
    @DisplayName("caso 1 com tag de ação no reply → tag REMOVIDA da ponte, nenhuma ação executada")
    void handoff_stripsLeftoverTagFromBridge() {
        fakeAi.enqueue(aiReply(
            "Vou te passar pro atendente. <pedido>{\"itens\":[{\"id\":\"x\",\"qtd\":1}]}</pedido>", true));
        fakeEvolution.enqueue("key-tag-1");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_HANDOFF);
        assertThat(fakeEvolution.calls()).isEqualTo(1);
        String content = (String) outboundRow(CONV).get("content");
        assertThat(content).isEqualTo("Vou te passar pro atendente.");
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    @Test
    @DisplayName("caso 1 com reply que é SÓ a tag → sem ponte útil, flipa sem enviar (como caso 2)")
    void handoff_onlyTagReply_flipsWithoutSending() {
        fakeAi.enqueue(aiReply("<pedido>{\"itens\":[]}</pedido>", true));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_HANDOFF);
        assertThat(fakeEvolution.calls()).isZero();
        assertThat(countOutbound(CONV)).isZero();
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    @Test
    @DisplayName("caso 6 com tag que nenhum handler interpretou (perfil errado) → tag removida antes do envio")
    void processed_stripsWrongProfileTag() {
        // company sem perfil de nicho: nenhum maybeProcess* age sobre <pedido_pizza> —
        // sem a rede de segurança, o JSON cru chegaria ao cliente.
        fakeAi.enqueue(aiReply(
            "Anotei seu pedido! <pedido_pizza>{\"sabores\":[\"calabresa\"]}</pedido_pizza>", false));
        fakeEvolution.enqueue("key-tag-6");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        String content = (String) outboundRow(CONV).get("content");
        assertThat(content).isEqualTo("Anotei seu pedido!");
        assertThat(handledByOf(CONV)).isEqualTo("ai");
    }

    @Test
    @DisplayName("caso 6 com reply que é SÓ tag não-interpretada → nada útil a enviar → FLIPPED_AI_BAD_REPLY")
    void processed_onlyLeftoverTag_flipsBadReply() {
        fakeAi.enqueue(aiReply("<pedido_pizza>{\"sabores\":[]}</pedido_pizza>", false));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_BAD_REPLY);
        assertThat(fakeEvolution.calls()).isZero();
        assertThat(countOutbound(CONV)).isZero();
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    // ---- casos 4-5 (falha da IA) --------------------------------------------

    @Test
    @DisplayName("caso 4: IA transiente esgota as 3 tentativas → flipa → FLIPPED_AI_EXHAUSTED")
    void flippedExhausted_whenAiTransientExhausted() {
        fakeAi.enqueue(new AiTransientException("503"));
        fakeAi.enqueue(new AiTransientException("503"));
        fakeAi.enqueue(new AiTransientException("503"));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_EXHAUSTED);
        assertThat(fakeAi.calls()).isEqualTo(3);   // 1 inicial + 2 retries
        assertThat(handledByOf(CONV)).isEqualTo("human");
        assertThat(countOutbound(CONV)).isZero();
    }

    @Test
    @DisplayName("caso 5: IA fatal → propaga imediato (sem retry), flipa → FLIPPED_AI_EXHAUSTED")
    void flippedExhausted_whenAiFatal() {
        fakeAi.enqueue(new AiException("400 bad request"));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_AI_EXHAUSTED);
        assertThat(fakeAi.calls()).isEqualTo(1);   // fatal não retenta
        assertThat(handledByOf(CONV)).isEqualTo("human");
    }

    // ---- caso 6 (caminho feliz) ---------------------------------------------

    @Test
    @DisplayName("caso 6: ¬needsHuman + reply → envia, persiste (outbound/ai/content/keyId) → PROCESSED")
    void processed_happyPath() {
        fakeAi.enqueue(aiReply("Olá! Tudo bem? 😊", false));
        fakeEvolution.enqueue("key-6");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(fakeEvolution.calls()).isEqualTo(1);
        assertThat(handledByOf(CONV)).isEqualTo("ai");   // NÃO flipa no caminho feliz
        assertThat(countOutbound(CONV)).isEqualTo(1);

        Map<String, Object> row = outboundRow(CONV);
        assertThat(row.get("direction")).isEqualTo("outbound");
        assertThat(row.get("sender")).isEqualTo("ai");
        assertThat(row.get("content")).isEqualTo("Olá! Tudo bem? 😊");
        assertThat(row.get("evolution_message_id")).isEqualTo("key-6");
    }

    // ---- boas-vindas (camada 5.21 #82) --------------------------------------

    @Test
    @DisplayName("welcome (#82): 1ª inbound do contato + welcome_message configurado → envia welcome ANTES da resposta (2 outbound)")
    void welcome_sentOnFirstInboundWhenConfigured() {
        // Configura a mensagem de boas-vindas no ai_settings da empresa (o @BeforeEach criou a linha).
        jdbcTemplate.update("update ai_settings set welcome_message = ? where company_id = ?",
            "Bem-vindo! Como posso ajudar?", COMPANY);
        // Semeia EXATAMENTE uma inbound do contato (representa a mensagem atual já persistida pelo
        // webhook em produção): countInboundForContact == 1 → é a primeira mensagem do contato.
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', ?)",
            COMPANY, CONV, "Oi, tudo bem?");
        // Dois envios pela Evolution: 1º o welcome, 2º a resposta da IA (ordem do caso 6).
        fakeAi.enqueue(aiReply("Claro, posso ajudar com agendamentos.", false));
        fakeEvolution.enqueue("key-welcome");
        fakeEvolution.enqueue("key-reply");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(fakeEvolution.calls()).isEqualTo(2);   // welcome + reply
        assertThat(countOutbound(CONV)).isEqualTo(2);
        assertThat(handledByOf(CONV)).isEqualTo("ai");

        // O conteúdo de boas-vindas foi persistido como mensagem outbound da IA.
        long welcomeRows = jdbcTemplate.queryForObject(
            "select count(*) from messages where conversation_id = ? and direction = 'outbound' "
                + "and content = ?", Long.class, CONV, "Bem-vindo! Como posso ajudar?");
        assertThat(welcomeRows).isEqualTo(1);
    }

    @Test
    @DisplayName("welcome (#82): NÃO é a 1ª inbound (já há mensagens prévias) → sem welcome (só a resposta)")
    void welcome_notSentWhenNotFirstInbound() {
        jdbcTemplate.update("update ai_settings set welcome_message = ? where company_id = ?",
            "Bem-vindo! Como posso ajudar?", COMPANY);
        // Duas inbounds: countInboundForContact == 2 → NÃO é a primeira mensagem.
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', ?)",
            COMPANY, CONV, "Mensagem antiga");
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', ?)",
            COMPANY, CONV, "Mensagem atual");
        fakeAi.enqueue(aiReply("Posso ajudar.", false));
        fakeEvolution.enqueue("key-reply");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(fakeEvolution.calls()).isEqualTo(1);   // só a resposta, sem welcome
        assertThat(countOutbound(CONV)).isEqualTo(1);
    }

    // ---- token tracking (camada 6.2.5) --------------------------------------

    @Test
    @DisplayName("token tracking (6.2.5): reply real da IA → outbound grava tokens_in/out + model")
    void tokenTracking_persistsTokensAndModelForAiReply() {
        // aiReply() devolve AiResponse com tokensIn=10, tokensOut=5 (helper desta suíte).
        // O OutboundService grava esses tokens + gemini.model (="test-model" no contexto de teste,
        // via AbstractIntegrationTest) na row outbound.
        fakeAi.enqueue(aiReply("Claro, posso ajudar!", false));
        fakeEvolution.enqueue("key-tok");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select tokens_in, tokens_out, model from messages "
                + "where conversation_id = ? and direction = 'outbound'", CONV);
        assertThat(row.get("tokens_in")).isEqualTo(10);
        assertThat(row.get("tokens_out")).isEqualTo(5);
        assertThat(row.get("model")).isEqualTo("test-model");
    }

    @Test
    @DisplayName("token tracking (6.2.5): welcome sintético (tokens 0/0) → grava NULL, não 0 (distingue 'sem IA')")
    void tokenTracking_syntheticWelcomeStoresNullNotZero() {
        // O welcome é um AiResponse sintético com tokens 0/0 (não passou pela IA). A row dele deve
        // ter tokens_in/out/model = NULL — distinguindo "mensagem sem IA" de "IA com custo zero".
        jdbcTemplate.update("update ai_settings set welcome_message = ? where company_id = ?",
            "Bem-vindo! Como posso ajudar?", COMPANY);
        jdbcTemplate.update(
            "insert into messages (company_id, conversation_id, direction, sender, content) "
                + "values (?, ?, 'inbound', 'contact', ?)",
            COMPANY, CONV, "Oi, tudo bem?");
        fakeAi.enqueue(aiReply("Claro, posso ajudar com agendamentos.", false));
        fakeEvolution.enqueue("key-welcome");
        fakeEvolution.enqueue("key-reply");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        // A row do welcome (conteúdo sintético): tokens/model NULL.
        Map<String, Object> welcomeRow = jdbcTemplate.queryForMap(
            "select tokens_in, tokens_out, model from messages where conversation_id = ? "
                + "and content = ?", CONV, "Bem-vindo! Como posso ajudar?");
        assertThat(welcomeRow.get("tokens_in")).isNull();
        assertThat(welcomeRow.get("tokens_out")).isNull();
        assertThat(welcomeRow.get("model")).isNull();
        // A row da resposta REAL da IA, em contraste, tem tokens preenchidos.
        Map<String, Object> replyRow = jdbcTemplate.queryForMap(
            "select tokens_in, tokens_out, model from messages where conversation_id = ? "
                + "and content = ?", CONV, "Claro, posso ajudar com agendamentos.");
        assertThat(replyRow.get("tokens_in")).isEqualTo(10);
        assertThat(replyRow.get("model")).isEqualTo("test-model");
    }

    // ---- scheduling intent (camada 5.15 #29) --------------------------------

    @Test
    @DisplayName("AiResponse com schedulingIntent → persiste em conversations.scheduling_intent + envia reply")
    void schedulingIntent_persistsToConversation() {
        SchedulingIntent intent = new SchedulingIntent(
            Instant.parse("2026-06-15T12:00:00Z"), "corte", "amanhã 14h", "high",
            "quero marcar amanhã às 14h");
        // AiResponse válido (caminho feliz, caso 6) COM intent — construtor de 7 args.
        fakeAi.enqueue(new AiResponse("Claro! Posso marcar.", false, null, 10, 5, 100L, intent));
        fakeEvolution.enqueue("key-intent");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(fakeEvolution.calls()).isEqualTo(1);   // reply foi enviado
        assertThat(countOutbound(CONV)).isEqualTo(1);

        // a coluna jsonb tem os campos esperados (snake_case, detected_at do servidor).
        Map<String, Object> intentRow = jdbcTemplate.queryForMap(
            "select scheduling_intent->>'service_hint' as service_hint, "
                + "scheduling_intent->>'when_hint' as when_hint, "
                + "scheduling_intent->>'urgency' as urgency, "
                + "scheduling_intent->>'raw_excerpt' as raw_excerpt, "
                + "scheduling_intent->>'detected_at' as detected_at "
                + "from conversations where id = ?", CONV);
        assertThat(intentRow.get("service_hint")).isEqualTo("corte");
        assertThat(intentRow.get("when_hint")).isEqualTo("amanhã 14h");
        assertThat(intentRow.get("urgency")).isEqualTo("high");
        assertThat(intentRow.get("raw_excerpt")).isEqualTo("quero marcar amanhã às 14h");
        assertThat((String) intentRow.get("detected_at")).startsWith("2026-06-15T12:00:00");
    }

    @Test
    @DisplayName("AiResponse SEM schedulingIntent → scheduling_intent fica null (maioria das msgs)")
    void noSchedulingIntent_columnStaysNull() {
        fakeAi.enqueue(aiReply("Sim, atendemos aos sábados.", false));   // sem intent (6-arg ctor)
        fakeEvolution.enqueue("key-nointent");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        Object intentCol = jdbcTemplate.queryForMap(
            "select scheduling_intent from conversations where id = ?", CONV)
            .get("scheduling_intent");
        assertThat(intentCol).isNull();
    }

    // ---- insights da camada 5.18 (cancelamento/reclamação/extracted_data) -----

    private static final ObjectMapper INSIGHTS_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("AiResponse com complaintIntent → persiste complaint_intent E força handoff (#52)")
    void complaintIntent_persistsAndForcesHandoff() {
        DetectedIntent complaint = new DetectedIntent(
            Instant.parse("2026-06-15T12:00:00Z"), "atraso no atendimento",
            "esperei uma hora e ninguém me atendeu");
        AiInsights insights = new AiInsights(null, complaint, null, null, null, null);
        // caminho feliz (caso 6): a IA respondeu needsHuman=false, mas a reclamação força handoff.
        fakeAi.enqueue(new AiResponse("Sinto muito pelo ocorrido.", false, null, 10, 5, 100L, null, insights));
        fakeEvolution.enqueue("key-complaint");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);   // o reply foi enviado normalmente
        assertThat(fakeEvolution.calls()).isEqualTo(1);
        assertThat(countOutbound(CONV)).isEqualTo(1);
        // #52: reclamação SEMPRE vira atendimento humano, independente do needsHuman da IA.
        assertThat(handledByOf(CONV)).isEqualTo("human");

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select complaint_intent->>'summary' as summary, "
                + "complaint_intent->>'raw_excerpt' as raw_excerpt, "
                + "complaint_intent->>'detected_at' as detected_at "
                + "from conversations where id = ?", CONV);
        assertThat(row.get("summary")).isEqualTo("atraso no atendimento");
        assertThat(row.get("raw_excerpt")).isEqualTo("esperei uma hora e ninguém me atendeu");
        assertThat((String) row.get("detected_at")).startsWith("2026-06-15T12:00:00");
    }

    @Test
    @DisplayName("AiResponse com extractedData → persiste em conversations.extracted_data")
    void extractedData_persistsToConversation() {
        com.fasterxml.jackson.databind.node.ObjectNode data = INSIGHTS_MAPPER.createObjectNode();
        data.put("nome", "Ana");
        data.put("email", "ana@ex.com");
        AiInsights insights = new AiInsights(null, null, data, null, null, null);
        fakeAi.enqueue(new AiResponse("Anotado, obrigado!", false, null, 10, 5, 100L, null, insights));
        fakeEvolution.enqueue("key-extracted");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(handledByOf(CONV)).isEqualTo("ai");   // dados coletados NÃO forçam handoff
        assertThat(countOutbound(CONV)).isEqualTo(1);

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "select extracted_data->>'nome' as nome, extracted_data->>'email' as email "
                + "from conversations where id = ?", CONV);
        assertThat(row.get("nome")).isEqualTo("Ana");
        assertThat(row.get("email")).isEqualTo("ana@ex.com");
    }

    // ---- appointment action (camada 5.19 #60) -------------------------------

    @Test
    @DisplayName("AiResponse com appointmentAction 'book' em slot válido → cria appointment do contato")
    void appointmentAction_book_createsAppointment() {
        // Semeia uma janela de disponibilidade ATIVA cobrindo um horário futuro (daqui 7 dias,
        // 10:00 local SP, dentro de 09:00–12:00 no weekday dessa data). O AppointmentService
        // valida contra essa janela antes de inserir.
        LocalDate targetDate = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(7);
        LocalDateTime targetLocal = LocalDateTime.of(targetDate, LocalTime.of(10, 0));
        int weekday = targetDate.getDayOfWeek().getValue() % 7;
        jdbcTemplate.update(
            "insert into availability_slots (company_id, weekday, starts_at, ends_at, slot_minutes, active) "
                + "values (?, ?, '09:00'::time, '12:00'::time, 30, true)", COMPANY, weekday);

        AppointmentAction action = new AppointmentAction("book", targetLocal.toString(), "Corte");
        AiInsights insights = new AiInsights(null, null, null, null, null, action);
        fakeAi.enqueue(new AiResponse("Agendado! Te espero.", false, null, 10, 5, 100L, null, insights));
        fakeEvolution.enqueue("key-appt");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(countOutbound(CONV)).isEqualTo(1);   // o reply foi enviado normalmente

        long appts = jdbcTemplate.queryForObject(
            "select count(*) from appointments where company_id = ? and contact_id = ? and status = 'scheduled'",
            Long.class, COMPANY, CONTACT);
        assertThat(appts).isEqualTo(1);
    }

    // ---- gate de horário comercial (camada 5.4) -----------------------------

    @Test
    @DisplayName("gate dentro do horário (seed dinâmico do @BeforeEach) → IA é chamada → PROCESSED")
    void gate_insideHours_callsAi() {
        // O seed do @BeforeEach já abre uma janela ao redor de agora → dentro do horário.
        fakeAi.enqueue(aiReply("Resposta da IA.", false));
        fakeEvolution.enqueue("key-inside");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(fakeAi.calls()).isEqualTo(1);          // IA FOI chamada
        assertThat(handledByOf(CONV)).isEqualTo("ai");
        assertThat(countOutbound(CONV)).isEqualTo(1);
    }

    @Test
    @DisplayName("gate fora do horário (dia atual closed) → IA NÃO chamada → PROCESSED_OUTSIDE_HOURS")
    void gate_outsideHours_respondsDefaultWithoutAi() {
        // Substitui a janela aberta do seed por um dia FECHADO no weekday atual.
        jdbcTemplate.update("delete from business_hours where company_id = ?", COMPANY);
        jdbcTemplate.update("insert into business_hours (company_id, weekday, closed) values (?, ?, true)",
            COMPANY, currentWeekdaySp());
        fakeEvolution.enqueue("key-outside");   // o envio da mensagem padrão usa a Evolution

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED_OUTSIDE_HOURS);
        assertThat(fakeAi.calls()).isZero();              // IA NÃO foi chamada
        assertThat(handledByOf(CONV)).isEqualTo("ai");    // segue 'ai' (foi a IA padrão)
        assertThat(countOutbound(CONV)).isEqualTo(1);

        Map<String, Object> row = outboundRow(CONV);
        assertThat(row.get("sender")).isEqualTo("ai");
        assertThat(row.get("content")).isEqualTo(
            "No momento estamos fora do horário de atendimento. "
                + "Retornaremos sua mensagem assim que possível.");
    }

    @Test
    @DisplayName("gate sem horários configurados → fallback aberto → IA é chamada → PROCESSED")
    void gate_noHoursConfigured_fallbackOpen() {
        // Sem nenhuma linha business_hours → BusinessHoursGate retorna true (fallback aberto).
        jdbcTemplate.update("delete from business_hours where company_id = ?", COMPANY);
        fakeAi.enqueue(aiReply("Resposta da IA.", false));
        fakeEvolution.enqueue("key-nohours");

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.PROCESSED);
        assertThat(fakeAi.calls()).isEqualTo(1);          // fallback aberto → IA roda
        assertThat(handledByOf(CONV)).isEqualTo("ai");
        assertThat(countOutbound(CONV)).isEqualTo(1);
    }

    // ---- casos 7-8 (falha de envio) -----------------------------------------

    @Test
    @DisplayName("caso 7: envio transiente esgota → flipa → FLIPPED_EVOLUTION_EXHAUSTED")
    void flippedEvolutionExhausted_whenSendTransientExhausted() {
        fakeAi.enqueue(aiReply("Resposta normal.", false));
        fakeEvolution.enqueue(new EvolutionTransientException("503"));
        fakeEvolution.enqueue(new EvolutionTransientException("503"));
        fakeEvolution.enqueue(new EvolutionTransientException("503"));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.FLIPPED_EVOLUTION_EXHAUSTED);
        assertThat(fakeEvolution.calls()).isEqualTo(3);
        assertThat(handledByOf(CONV)).isEqualTo("human");
        assertThat(countOutbound(CONV)).isZero();   // não persiste se não enviou
    }

    @Test
    @DisplayName("caso 8: envio fatal (4xx) → SEM flip → EVOLUTION_CONFIG_ERROR")
    void evolutionConfigError_whenSendFatal() {
        fakeAi.enqueue(aiReply("Resposta normal.", false));
        fakeEvolution.enqueue(new EvolutionException("401 unauthorized"));

        OutboundOutcome outcome = service.process(event());

        assertThat(outcome).isEqualTo(OutboundOutcome.EVOLUTION_CONFIG_ERROR);
        assertThat(fakeEvolution.calls()).isEqualTo(1);   // fatal não retenta
        assertThat(handledByOf(CONV)).isEqualTo("ai");    // NÃO flipa (canal quebrado)
        assertThat(countOutbound(CONV)).isZero();
    }

    // ---- caso 9.2 (credenciais ausentes) ------------------------------------

    @Test
    @DisplayName("caso 9.2: credenciais ausentes (instância sumiu) → SEM flip → EVOLUTION_CONFIG_ERROR")
    void evolutionConfigError_whenCredentialsMissing() {
        UUID unknownInstance = UUID.fromString("a1000000-0000-0000-0000-0000000000ff");
        fakeAi.enqueue(aiReply("Resposta normal.", false));

        OutboundOutcome outcome = service.process(eventWith(CONV, unknownInstance));

        assertThat(outcome).isEqualTo(OutboundOutcome.EVOLUTION_CONFIG_ERROR);
        assertThat(fakeEvolution.calls()).isZero();   // falha no Bloco 3b, antes de enviar
        assertThat(handledByOf(CONV)).isEqualTo("ai");
        assertThat(countOutbound(CONV)).isZero();
    }

    // ---- fakes --------------------------------------------------------------

    /**
     * Fila controlável: cada chamada faz poll() — se o item é RuntimeException, lança;
     * senão retorna como T. Fila vazia = teste mal configurado (lança IllegalState).
     * Conta as chamadas para asserir o número de tentativas (retry).
     */
    abstract static class FakeQueue {
        private final Queue<Object> queue = new ArrayDeque<>();
        private final AtomicInteger callCount = new AtomicInteger();

        void enqueue(Object responseOrException) {
            queue.add(responseOrException);
        }

        void reset() {
            queue.clear();
            callCount.set(0);
        }

        int calls() {
            return callCount.get();
        }

        Object next() {
            callCount.incrementAndGet();
            Object item = queue.poll();
            if (item == null) {
                throw new IllegalStateException("fake queue empty — teste mal configurado");
            }
            if (item instanceof RuntimeException e) {
                throw e;
            }
            return item;
        }
    }

    static class FakeAiProvider extends FakeQueue implements AiProvider {
        @Override
        public AiResponse generate(Prompt prompt) {
            return (AiResponse) next();
        }
    }

    static class FakeEvolutionSender extends FakeQueue implements EvolutionSender {
        @Override
        public String sendText(String instanceName, String token, String number, String text) {
            return (String) next();
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeAiProvider fakeAiProvider() {
            return new FakeAiProvider();
        }

        @Bean
        @Primary
        FakeEvolutionSender fakeEvolutionSender() {
            return new FakeEvolutionSender();
        }
    }
}
