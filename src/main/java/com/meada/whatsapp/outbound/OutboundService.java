package com.meada.whatsapp.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meada.whatsapp.ai.AiException;
import com.meada.whatsapp.ai.AiInsights;
import com.meada.whatsapp.ai.AiProvider;
import com.meada.whatsapp.ai.AiTransientException;
import com.meada.whatsapp.ai.AiResponse;
import com.meada.whatsapp.ai.DetectedIntent;
import com.meada.whatsapp.ai.Prompt;
import com.meada.whatsapp.ai.PromptBuilder;
import com.meada.whatsapp.ai.SchedulingIntent;
import com.meada.whatsapp.messaging.BusinessHours;
import com.meada.whatsapp.messaging.BusinessHoursRepository;
import com.meada.whatsapp.messaging.ContactRepository;
import com.meada.whatsapp.messaging.ConversationRepository;
import com.meada.whatsapp.messaging.EvolutionCredentials;
import com.meada.whatsapp.messaging.MessageDirection;
import com.meada.whatsapp.messaging.MessageSender;
import com.meada.whatsapp.messaging.MessageRepository;
import com.meada.whatsapp.messaging.WhatsappInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Orquestra a resposta da IA a uma mensagem inbound: monta o prompt, chama a IA com
 * retry, e — conforme o resultado — envia a resposta pela Evolution e/ou transfere a
 * conversa para atendimento humano. É o coração da camada 3 (matriz de fluxo da Fase 3.3).
 *
 * <p>Na Fase 3.3 o método {@link #process} é chamado SÍNCRONO (pelos testes). Na 3.4
 * um {@code @TransactionalEventListener(AFTER_COMMIT)} + {@code @Async} o dispara a
 * partir do {@code MessageInboundProcessedEvent} publicado pelo WebhookService.
 *
 * <h2>Matriz de fluxo (9 casos + pré-condição) → {@link OutboundOutcome}</h2>
 * <ul>
 *   <li>pré: conversa não é 'ai' (humano assumiu) ou sumiu → SKIPPED_NOT_AI.
 *   <li>1: needsHuman + reply → envia, grava, flipa → FLIPPED_AI_HANDOFF.
 *   <li>2: needsHuman sem reply → flipa direto → FLIPPED_AI_HANDOFF.
 *   <li>3: ¬needsHuman sem reply (contrato quebrado) → flipa → FLIPPED_AI_BAD_REPLY.
 *   <li>4/5: IA falha (transient esgotada OU fatal) → flipa → FLIPPED_AI_EXHAUSTED.
 *   <li>6: ¬needsHuman + reply → envia, grava → PROCESSED.
 *   <li>7: envio transient esgotado → flipa → FLIPPED_EVOLUTION_EXHAUSTED.
 *   <li>8: envio fatal (4xx) → SEM flip → EVOLUTION_CONFIG_ERROR.
 *   <li>9: phone/credenciais ausentes (entidade sumiu) → SEM flip → EVOLUTION_CONFIG_ERROR.
 * </ul>
 *
 * <p>Por que casos 8/9 NÃO flipam: o canal está quebrado (auth/config); um humano
 * usaria o MESMO canal e também falharia — flipar só empilharia backlog invisível.
 * ERROR alertável é a resposta certa, não transferência.
 */
@Service
public class OutboundService {

    private static final Logger log = LoggerFactory.getLogger(OutboundService.class);

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final WhatsappInstanceRepository whatsappInstanceRepository;
    private final MessageRepository messageRepository;
    private final PromptBuilder promptBuilder;
    private final AiProvider aiProvider;
    private final EvolutionSender evolutionSender;
    private final RetryRunner retryRunner;

    private final BusinessHoursRepository businessHoursRepository;
    private final BusinessHoursGate businessHoursGate;
    private final ObjectMapper objectMapper;

    private final int maxAttempts;
    private final List<Duration> backoffs;

    // Fuso do tenant para avaliar o horário comercial. HARDCODED no MVP (tenants BR).
    // TODO: coluna companies.timezone quando virar multi-país.
    private static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    // Resposta automática quando a empresa está fora do horário (camada 5.4). Hardcoded
    // pt-BR; pode virar campo editável no ai_settings em fase futura.
    private static final String OUTSIDE_HOURS_REPLY =
        "No momento estamos fora do horário de atendimento. "
            + "Retornaremos sua mensagem assim que possível.";

    public OutboundService(ConversationRepository conversationRepository,
                           ContactRepository contactRepository,
                           WhatsappInstanceRepository whatsappInstanceRepository,
                           MessageRepository messageRepository,
                           PromptBuilder promptBuilder,
                           AiProvider aiProvider,
                           EvolutionSender evolutionSender,
                           RetryRunner retryRunner,
                           OutboundRetryProperties retryProps,
                           BusinessHoursRepository businessHoursRepository,
                           BusinessHoursGate businessHoursGate,
                           ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.contactRepository = contactRepository;
        this.whatsappInstanceRepository = whatsappInstanceRepository;
        this.messageRepository = messageRepository;
        this.promptBuilder = promptBuilder;
        this.aiProvider = aiProvider;
        this.evolutionSender = evolutionSender;
        this.retryRunner = retryRunner;
        this.businessHoursRepository = businessHoursRepository;
        this.businessHoursGate = businessHoursGate;
        this.objectMapper = objectMapper;
        this.maxAttempts = retryProps.maxAttempts();
        // converte uma vez (lista YAML de millis → Durations). O RetryRunner valida
        // o invariante backoffs.size() == maxAttempts-1 em cada chamada.
        this.backoffs = retryProps.backoffMs().stream().map(Duration::ofMillis).toList();
    }

    /**
     * Processa uma mensagem inbound: gera a resposta da IA e a despacha conforme a
     * matriz de fluxo. Determinístico — toda situação tem um único caminho até um
     * {@link OutboundOutcome}.
     *
     * @param event identidade do disparo (userMessage) + ids do contexto
     * @return o desfecho (também é o que o log/observabilidade reporta)
     */
    public OutboundOutcome process(MessageInboundProcessedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        UUID conversationId = event.conversationId();

        // ---- BLOCO 0 — pré-condição: só processa IA se a conversa ainda é 'ai' ----
        Optional<String> handledBy = conversationRepository.findHandledBy(conversationId);
        if (handledBy.isEmpty() || !"ai".equals(handledBy.get())) {
            // IA não rodou → sem aiResponse, sem reason.
            return logOutcome(OutboundOutcome.SKIPPED_NOT_AI, event, null, null);
        }

        // ---- BLOCO 0.5 — gate de horário comercial (camada 5.4) ----
        // Determinístico, ANTES de chamar a IA: se o tenant está fora do horário,
        // responde a mensagem padrão SEM custo de Gemini. Fallback aberto quando não há
        // horários configurados (BusinessHoursGate). Roda DEPOIS do BLOCO 0: se um humano
        // assumiu, ele responde no horário dele — o gate é da IA automática, não da conversa.
        List<BusinessHours> hours = businessHoursRepository.findByCompany(event.companyId());
        LocalDateTime nowLocal = LocalDateTime.now(TENANT_ZONE);
        int weekday = nowLocal.getDayOfWeek().getValue() % 7;   // ISO Mon=1..Sun=7 → Sun=0..Sat=6
        if (!businessHoursGate.isInsideHours(hours, weekday, nowLocal.toLocalTime())) {
            return respondOutsideHours(event, conversationId);
        }

        // ---- BLOCO 1 — monta o prompt e chama a IA com retry ----
        // PromptBuilder pode lançar se a config do tenant (ai_settings) faltar: é erro
        // de PROVISIONAMENTO, não de runtime de IA — deixamos propagar (visibilidade),
        // NÃO viramos humano silenciosamente.
        Prompt prompt = promptBuilder.build(event.companyId(), conversationId, event.userMessage());
        AiResponse aiResponse;
        try {
            aiResponse = retryRunner.runWithBackoff(
                () -> aiProvider.generate(prompt), maxAttempts, backoffs, AiTransientException.class);
        } catch (AiException e) {
            // só AiTransientException é retentável; fatal (AiException puro) o runner
            // relança imediato sem retry (caso 5, simétrico ao Evolution). O catch de
            // AiException pega AMBOS — transient esgotada (após N tentativas) e fatal —
            // porque AiTransientException extends AiException. Casos 4+5 colapsam aqui.
            conversationRepository.markHandledByHuman(conversationId);
            // IA falhou (sem AiResponse válido); o detalhe do erro vai no warn abaixo.
            log.warn("outbound: AI call failed for conversation {} ({})", conversationId, e.getMessage());
            return logOutcome(OutboundOutcome.FLIPPED_AI_EXHAUSTED, event, null, null);
        }

        // ---- BLOCO 2 — branching pelo AiResponse ----
        boolean hasReply = aiResponse.reply() != null && !aiResponse.reply().isBlank();

        if (aiResponse.needsHuman()) {
            if (hasReply) {
                // caso 1: persiste a intent (#29) e os insights (5.18) ANTES de enviar, manda a
                // resposta-ponte, grava, depois flipa. Casos 2/3 (sem reply efetivo) NÃO persistem.
                persistSchedulingIntent(conversationId, aiResponse);
                persistInsights(conversationId, aiResponse);
                Optional<OutboundOutcome> sendFailure = sendAndPersist(event, conversationId, aiResponse);
                if (sendFailure.isPresent()) {
                    return sendFailure.get();   // falha de envio domina (casos 7/8/9) — já logado lá
                }
                conversationRepository.markHandledByHuman(conversationId);
                return logOutcome(OutboundOutcome.FLIPPED_AI_HANDOFF, event, aiResponse, null);
            }
            // caso 2: precisa de humano e não há reply — flipa direto, sem enviar.
            conversationRepository.markHandledByHuman(conversationId);
            return logOutcome(OutboundOutcome.FLIPPED_AI_HANDOFF, event, aiResponse, null);
        }

        // needsHuman == false
        if (!hasReply) {
            // caso 3: contrato quebrado — a IA disse que NÃO precisa de humano mas não
            // produziu resposta. Flipa e sinaliza para investigar prompt/modelo.
            conversationRepository.markHandledByHuman(conversationId);
            return logOutcome(OutboundOutcome.FLIPPED_AI_BAD_REPLY, event, aiResponse, null);
        }

        // caso 6: caminho feliz — persiste a intent (#29) e os insights (5.18) ANTES de enviar,
        // depois envia e grava.
        persistSchedulingIntent(conversationId, aiResponse);
        persistInsights(conversationId, aiResponse);
        Optional<OutboundOutcome> sendFailure = sendAndPersist(event, conversationId, aiResponse);
        if (sendFailure.isPresent()) {
            return sendFailure.get();   // casos 7/8/9 — já logado lá
        }
        return logOutcome(OutboundOutcome.PROCESSED, event, aiResponse, null);
    }

    /**
     * Responde a mensagem padrão de fora-de-horário (camada 5.4). Reusa o caminho de
     * envio/persistência do caso 6 via um {@link AiResponse} sintético (reply=padrão,
     * needsHuman=false, métricas zeradas — não houve IA). A conversa segue
     * handled_by='ai'. Falhas de envio (casos 7/8/9) ainda são cobertas pelo
     * sendAndPersist e dominam o outcome se ocorrerem.
     *
     * <p>logOutcome recebe aiResponse=null de propósito (decisão cravada): não houve
     * chamada de IA, então o log NÃO traz tokens/latency — sairiam todos 0, enganoso.
     */
    private OutboundOutcome respondOutsideHours(MessageInboundProcessedEvent event,
                                                UUID conversationId) {
        AiResponse synthetic = new AiResponse(OUTSIDE_HOURS_REPLY, false, null, 0, 0, 0L);
        Optional<OutboundOutcome> sendFailure = sendAndPersist(event, conversationId, synthetic);
        if (sendFailure.isPresent()) {
            return sendFailure.get();   // casos 7/8/9 de envio — já logado lá
        }
        // aiResponse=null no log: não houve IA, o log sai limpo (sem tokens/latency).
        return logOutcome(OutboundOutcome.PROCESSED_OUTSIDE_HOURS, event, null, null);
    }

    /**
     * Resolve destinatário + credenciais, envia pela Evolution com retry e persiste a
     * mensagem outbound. Compartilhado pelos casos 1 e 6 (ambos enviam).
     *
     * @return {@link Optional#empty()} em SUCESSO (mensagem enviada e persistida — o
     *         caller decide o outcome final: PROCESSED ou FLIPPED_AI_HANDOFF); um
     *         {@code Optional.of(outcome)} de FALHA (casos 7/8/9) caso enviar/gravar
     *         não conclua — o caller propaga esse outcome direto.
     */
    private Optional<OutboundOutcome> sendAndPersist(MessageInboundProcessedEvent event,
                                                     UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();

        // ---- BLOCO 3a — destinatário ----
        Optional<String> phone = contactRepository.findPhoneByConversationId(conversationId);
        if (phone.isEmpty() || phone.get().isBlank()) {
            // caso 9.1: defesa contra conversa/contato removidos por correção MANUAL de
            // dados (LGPD, reconciliação) entre o evento e este processamento async. O
            // caminho de APLICAÇÃO é inalcançável (phone_number NOT NULL, FK contact_id
            // ON DELETE RESTRICT, JOIN não filtra deleted_at) — por isso sem teste de
            // integração. Simétrico ao guard de credenciais em 3b. SEM flip.
            return Optional.of(logOutcome(
                OutboundOutcome.EVOLUTION_CONFIG_ERROR, event, aiResponse, "missing_phone"));
        }

        // ---- BLOCO 3b — credenciais da instância ----
        Optional<EvolutionCredentials> creds =
            whatsappInstanceRepository.findEvolutionCredentials(event.whatsappInstanceId());
        if (creds.isEmpty()) {
            // caso 9.2: instância sumiu. SEM flip.
            return Optional.of(logOutcome(
                OutboundOutcome.EVOLUTION_CONFIG_ERROR, event, aiResponse, "missing_credentials"));
        }

        // ---- BLOCO 3c — envio com retry ----
        String keyId;
        try {
            keyId = retryRunner.runWithBackoff(
                () -> evolutionSender.sendText(
                    creds.get().instanceName(), creds.get().token(), phone.get(), reply),
                maxAttempts, backoffs, EvolutionTransientException.class);
        } catch (EvolutionTransientException e) {
            // caso 7: transient esgotado após retries — flipa (humano tenta de novo).
            conversationRepository.markHandledByHuman(conversationId);
            log.warn("outbound: Evolution transient exhausted ({})", e.getMessage());
            return Optional.of(logOutcome(
                OutboundOutcome.FLIPPED_EVOLUTION_EXHAUSTED, event, aiResponse, null));
        } catch (EvolutionException e) {
            // caso 8: fatal (4xx/parse) — canal quebrado, SEM flip (humano falharia igual).
            log.warn("outbound: Evolution fatal error ({})", e.getMessage());
            return Optional.of(logOutcome(
                OutboundOutcome.EVOLUTION_CONFIG_ERROR, event, aiResponse, "evolution_fatal"));
        }

        // ---- BLOCO 4 — persiste a outbound ----
        // janela de crash: a mensagem JÁ foi enviada ao cliente; se o insert falhar,
        // logamos para reconciliação manual. insertIfNew é idempotente pelo evolution_message_id.
        Optional<?> inserted = messageRepository.insertIfNew(
            event.companyId(), conversationId,
            MessageDirection.OUTBOUND, MessageSender.AI, reply, keyId);
        if (inserted.isEmpty()) {
            log.warn("outbound: evolution_message_id {} already persisted for conversation {} "
                + "(duplicate processing?)", keyId, conversationId);
        }
        return Optional.empty();   // sucesso (o caller loga o outcome final PROCESSED/HANDOFF)
    }

    /**
     * Persiste a intenção de agendamento detectada (camada 5.15 #29) em
     * conversations.scheduling_intent. No-op quando {@code aiResponse.schedulingIntent()}
     * é null (maioria das mensagens) — evita UPDATE em toda mensagem.
     *
     * <p>Serializa o {@link SchedulingIntent} para JSON snake_case (chaves batendo com o
     * que o painel lê via SDK: detected_at, service_hint, when_hint, urgency, raw_excerpt).
     * Não usa o ObjectMapper "as-is" sobre o record (evita acoplar o nome dos campos Java à
     * forma do jsonb) — monta um ObjectNode explícito. detected_at em ISO-8601 (toString do
     * Instant).
     *
     * <p>Falha de persistência da intent NÃO derruba o atendimento: logamos warn e seguimos
     * (a resposta ao cliente é mais importante que a marcação interna). Diferente dos
     * UPDATEs de handoff, que são parte do contrato de fluxo.
     */
    private void persistSchedulingIntent(UUID conversationId, AiResponse aiResponse) {
        SchedulingIntent intent = aiResponse.schedulingIntent();
        if (intent == null) {
            return;
        }
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("detected_at", intent.detectedAt().toString());
            node.put("service_hint", intent.serviceHint());   // put(String, null) → JSON null
            node.put("when_hint", intent.whenHint());
            node.put("urgency", intent.urgency());
            node.put("raw_excerpt", intent.rawExcerpt());
            conversationRepository.updateSchedulingIntent(
                conversationId, objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("outbound: failed to persist scheduling_intent for conversation {} ({})",
                conversationId, e.getMessage());
        }
    }

    /**
     * Persiste os insights OPCIONAIS da camada 5.18 (cancelamento #51, reclamação #52,
     * extracted_data #53, memory_update #55, detected_tone #58). No-op quando nada foi
     * detectado ({@code insights.hasAny() == false}) — caso da maioria das mensagens.
     *
     * <p>Cada sub-objeto presente é persistido na coluna correspondente. As detecções por
     * conversa (cancelamento, reclamação, extracted_data) vão em conversations; as por contato
     * (memory_update, detected_tone) precisam do contact_id, resolvido via
     * {@link ConversationRepository#findContactIdByConversation}. Os DetectedIntent viram um
     * ObjectNode explícito snake_case (detected_at, summary, raw_excerpt), como em
     * {@link #persistSchedulingIntent}; extracted_data e memory_update (JsonNode livre) são
     * escritos direto.
     *
     * <p>#52: quando há complaint_intent, FORÇA o handoff (handled_by='human') após persistir —
     * reclamação sempre vira atendimento humano, independentemente do needsHuman da IA.
     *
     * <p>Falha de persistência NÃO derruba o atendimento: logamos warn e seguimos (a resposta
     * ao cliente é mais importante que a marcação interna), igual ao persistSchedulingIntent.
     */
    private void persistInsights(UUID conversationId, AiResponse aiResponse) {
        AiInsights insights = aiResponse.insights();
        if (insights == null || !insights.hasAny()) {
            return;
        }
        try {
            if (insights.cancellationIntent() != null) {
                conversationRepository.updateCancellationIntent(
                    conversationId, detectedIntentJson(insights.cancellationIntent()));
            }
            if (insights.complaintIntent() != null) {
                conversationRepository.updateComplaintIntent(
                    conversationId, detectedIntentJson(insights.complaintIntent()));
            }
            if (insights.extractedData() != null) {
                conversationRepository.updateExtractedData(
                    conversationId, objectMapper.writeValueAsString(insights.extractedData()));
            }

            // Detecções por-contato (memory_update #55, detected_tone #58): precisam do contact_id.
            if (insights.memoryUpdate() != null || insights.detectedTone() != null) {
                Optional<UUID> contactId =
                    conversationRepository.findContactIdByConversation(conversationId);
                if (contactId.isPresent()) {
                    if (insights.memoryUpdate() != null) {
                        contactRepository.updateMemory(
                            contactId.get(), objectMapper.writeValueAsString(insights.memoryUpdate()));
                    }
                    if (insights.detectedTone() != null) {
                        contactRepository.updateDetectedTone(contactId.get(), insights.detectedTone());
                    }
                } else {
                    log.warn("outbound: contact not found for conversation {} — memory/tone skipped",
                        conversationId);
                }
            }

            // #52: reclamação detectada SEMPRE força handoff (após persistir a marcação).
            if (insights.complaintIntent() != null) {
                conversationRepository.markHandledByHuman(conversationId);
            }
        } catch (Exception e) {
            log.warn("outbound: failed to persist insights for conversation {} ({})",
                conversationId, e.getMessage());
        }
    }

    /**
     * Serializa um {@link DetectedIntent} para JSON snake_case (detected_at, summary,
     * raw_excerpt) — mesma técnica do persistSchedulingIntent: ObjectNode explícito para não
     * acoplar o nome dos campos Java à forma do jsonb. detected_at em ISO-8601.
     */
    private String detectedIntentJson(DetectedIntent intent) throws Exception {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("detected_at", intent.detectedAt().toString());
        node.put("summary", intent.summary());        // put(String, null) → JSON null
        node.put("raw_excerpt", intent.rawExcerpt());
        return objectMapper.writeValueAsString(node);
    }

    /**
     * Log estruturado (key=value, espelha o WebhookService da camada 2) do desfecho.
     * Nível pelo {@link OutboundOutcome#logLevel()}. Nunca loga reply/conteúdo (PII).
     *
     * <p>Campos: sempre {@code outcome, company_id, conversation_id}; se
     * {@code aiResponse != null} (IA rodou com sucesso) também {@code tokens_in,
     * tokens_out, latency_ms, needs_human}; se {@code reason != null} (ramos ERROR)
     * também {@code reason}. Retorna o outcome para o caller encadear no return.
     *
     * @param aiResponse métricas da IA, ou null se a IA não rodou (SKIPPED) ou falhou
     *                   (FLIPPED_AI_EXHAUSTED) — nesses casos não há tokens a logar.
     * @param reason     motivo dos EVOLUTION_CONFIG_ERROR (missing_phone /
     *                   missing_credentials / evolution_fatal); null nos demais.
     */
    private OutboundOutcome logOutcome(OutboundOutcome outcome, MessageInboundProcessedEvent event,
                                       AiResponse aiResponse, String reason) {
        StringBuilder msg = new StringBuilder("outbound outcome=").append(outcome.name())
            .append(" company_id=").append(event.companyId())
            .append(" conversation_id=").append(event.conversationId());
        if (aiResponse != null) {
            msg.append(" tokens_in=").append(aiResponse.tokensIn())
               .append(" tokens_out=").append(aiResponse.tokensOut())
               .append(" latency_ms=").append(aiResponse.latencyMs())
               .append(" needs_human=").append(aiResponse.needsHuman());
        }
        if (reason != null) {
            msg.append(" reason=").append(reason);
        }
        switch (outcome.logLevel()) {
            case ERROR -> log.error(msg.toString());
            case WARN -> log.warn(msg.toString());
            default -> log.info(msg.toString());
        }
        return outcome;
    }
}
