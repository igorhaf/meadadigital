package com.meada.whatsapp.outbound;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.ai.AiException;
import com.meada.whatsapp.ai.AiProvider;
import com.meada.whatsapp.ai.AiResponse;
import com.meada.whatsapp.ai.AiTransientException;
import com.meada.whatsapp.ai.Prompt;
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
        jdbcTemplate.update("insert into business_hours (company_id, weekday, closed, opens_at, closes_at) "
            + "values (?, 1, false, '09:00'::time, '18:00'::time)", COMPANY);
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
