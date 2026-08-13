package com.meada.outbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meada.ai.AiException;
import com.meada.ai.AiInsights;
import com.meada.ai.AiProvider;
import com.meada.ai.AiTransientException;
import com.meada.ai.AiResponse;
import com.meada.ai.DetectedIntent;
import com.meada.ai.Prompt;
import com.meada.ai.PromptBuilder;
import com.meada.ai.SchedulingIntent;
import com.meada.appointments.AppointmentService;
import com.meada.messaging.AiSettingsRepository;
import com.meada.messaging.BusinessHours;
import com.meada.messaging.BusinessHoursRepository;
import com.meada.messaging.ContactRepository;
import com.meada.messaging.ConversationRepository;
import com.meada.messaging.EvolutionCredentials;
import com.meada.messaging.MessageDirection;
import com.meada.messaging.MessageSender;
import com.meada.messaging.MessageRepository;
import com.meada.messaging.WhatsappInstanceRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * Formato canônico de TODA tag de ação da IA: {@code <nome_tag>{json}</nome_tag>}.
     * Rede de segurança para tag que sobrou sem interpretação — emitida junto com
     * needsHuman=true (o handoff não roda a cadeia de handlers) ou tag de perfil
     * errado/alucinada (o early-return de perfil devolve o reply intacto). O cliente
     * NUNCA recebe JSON cru.
     */
    private static final Pattern LEFTOVER_TAG =
        Pattern.compile("<([a-z][a-z0-9_]*)>\\s*\\{.*?\\}\\s*</\\1>", Pattern.DOTALL);

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
    private final AppointmentService appointmentService;
    private final AiSettingsRepository aiSettingsRepository;
    private final com.meada.admin.health.ErrorLogger errorLogger;
    // Camada 7.1 (perfil sushi): pós-processa a resposta da IA para extrair a tag <pedido>,
    // criar o pedido e remover a tag antes de enviar ao cliente. Só age para profile_id='sushi'.
    private final com.meada.profiles.CompanyProfileRepository companyProfileRepository;
    private final com.meada.profiles.sushi.orders.OrderConfirmHandler orderConfirmHandler;
    // Camada 7.3 (perfil restaurant): pós-processa a tag <reserva> — cria a reserva e remove a tag.
    // Camada 7.4 (perfil dental): pós-processa a tag <consulta> — cria a consulta e remove a tag.
    // Camada 7.5 (perfil salon): pós-processa a tag <agendamento> — cria o agendamento e remove a tag.
    // Camada 7.6 (perfil pousada): pós-processa a tag <reserva_pousada> — cria a reserva e remove a tag.
    // Camada 7.7 (perfil academia): pós-processa a tag <matricula> — cria a matrícula e remove a tag.
    // Camada 7.9 (perfil oficina): pós-processa <ordem_servico> (abre OS) e <aprovacao_os> (muta estado).
    // Camada 8.0 (perfil nutri): pós-processa <consulta_nutri> (agenda) e <entrega_plano> (entrega o body exato).
    // Camada 8.1 (perfil barbearia): pós-processa <agendamento_barbearia> (marca), <fila_barbearia>
    // (enfileira) e <confirmacao_barbearia> (o cliente confirma/cancela o horário — onda 1 do backlog #1).
    // Camada 8.2 (perfil eventos): pós-processa <proposta_evento> (abre proposta) e <aprovacao_proposta> (muta estado).
    // Camada 8.3 (perfil estetica): pós-processa <agendamento_estetica> (agenda, consome saldo) e <compra_pacote> (cria pacote pendente).
    // Camada 8.4 (perfil comida): pós-processa a tag <pedido_comida> — cria o pedido (nasce 'aguardando')
    // e remove a tag antes de enviar ao cliente. Só age para profile_id='comida'. O aceite/recusa é
    // ação HUMANA no painel (não há handler de aceite — é o ponto central da ESCAPADA 1 do delivery).
    // Camada 8.6 (perfil pizzaria): pós-processa a tag <pedido_pizza> — cria o pedido (nasce 'aguardando')
    // e remove a tag antes de enviar. Só age para profile_id='pizzaria'. Inclui a ESCAPADA meio-a-meio
    // (frações de sabor, preço pela regra do MAIOR VALOR — recalculado no backend).
    // Camada 8.9 (perfil adega): pós-processa a tag <pedido_adega> — cria o pedido (nasce 'aguardando')
    // e remove a tag antes de enviar. Só age para profile_id='adega'. Inclui a ESCAPADA +18 (a tag sem
    // age_confirmed=true não cria pedido — trava de faixa etária na venda de álcool).
    // Camada 8.19 (perfil escola): pós-processa <matricula_escola> (matrícula-assinatura, 2 modos:
    // student_id OU new_student) e <visita_escola> (agenda leve dia+período). Só agem p/ profile_id='escola'.
    // Camada 8.14 (perfil atelie): <proposta_atelie> ABRE a proposta (rascunho); <aprovacao_atelie> MUTA
    // o estado (aprovada/recusada, só se 'orcada') — gate de aprovação em 2 fases. Só agem p/ profile_id='atelie'.
    // Camada 8.7 (perfil casamento): <proposta_casamento> ABRE a proposta (rascunho); <aprovacao_casamento>
    // MUTA o estado (aprovada/recusada, só se 'orcada') — gate de aprovação em 2 fases. Só p/ profile_id='casamento'.
    // Camada 8.17 (perfil concessionaria): <testdrive_carro> agenda test-drive (conflito por vendedor);
    // <lead_carro> registra interesse de compra (funil). Só agem p/ profile_id='concessionaria'.
    // Onda 1 do backlog concessionária: <desejo_carro> registra a lista de desejos (#1) e
    // <confirmacao_testdrive> fecha o loop do lembrete SIM/CANCELAR (#3).
    // Camada 8.10 (perfil lavanderia): <pedido_lavanderia> cria o pedido (nasce 'aguardando'; 2 datas
    // coleta+entrega com turnaround). Só age p/ profile_id='lavanderia'.
    // Camada 8.11 (perfil dermatologia): <consulta_derma> agenda consulta (conflito por profissional,
    // 2 modos paciente); <entrega_preparo> entrega READ-ONLY a nota de preparo do tipo (verbatim, barreira
    // de contato). Só agem p/ profile_id='dermatologia'.
    // Camada 8.16 (perfil fotografia): <sessao_foto> agenda a sessão (conflito por profissional,
    // snapshot de pacote, end_at + delivery_due_date materializados); <entrega_material> entrega
    // READ-ONLY o link do material (barreira de contato). Só agem p/ profile_id='fotografia'.
    // Camada 8.20 (perfil cursos): <matricula_curso> matricula (assinatura, anti-dupla por curso);
    // <entrega_modulo> entrega READ-ONLY o conteúdo do próximo módulo (barreira de contato + grava
    // progresso). Só agem p/ profile_id='cursos'.
    // Camada 8.21 (perfil lingerie): <pedido_lingerie> cria o pedido de varejo (variante×estoque,
    // decremento transacional → out_of_stock aborta; gate de aceite humano). Só age p/ profile_id='lingerie'.
    // Camada 8.22 (perfil moda_infantil): <pedido_moda_infantil> cria o pedido de varejo infantil
    // (variante faixa-etária×cor + estoque, decremento transacional, restock no cancelamento). Só age
    // p/ profile_id='moda_infantil'.
    // Camada 8.23 (perfil las): <pedido_las> cria o pedido de varejo de lãs (variante cor×dye_lot +
    // estoque, decremento transacional, regra same_lot_guaranteed). Só age p/ profile_id='las'.
    // Camada 8.8 (perfil padaria): <encomenda_padaria> cria o pedido de padaria/confeitaria (cardápio +
    // personalização + made_to_order/lead time + fulfillment + gate de aceite). Só age p/ profile_id='padaria'.
    // Camada 8.12 (perfil otica — HÍBRIDO): <exame_otica> agenda o exame (conflito por profissional);
    // <encomenda_otica> cria a encomenda de óculos (receita administrativa + lead time + gate de aceite).
    // Só agem p/ profile_id='otica'.
    // Camada 8.15 (perfil papelaria): <pedido_papelaria> cria a encomenda gráfica (tiragem +
    // personalização + lead + gate de aceite); <aprovacao_arte> CAPTURA a aprovação da arte do cliente
    // (muta o estado de um pedido existente em 'arte_aprovacao'). Só agem p/ profile_id='papelaria'.
    // Camada 8.18 (perfil viagens): <proposta_viagem> ABRE a proposta de viagem (rascunho);
    // <aprovacao_viagem> CAPTURA a aprovação/recusa (muta o estado de uma proposta 'orcada' existente).
    // Só agem p/ profile_id='viagens'.
    // Camada 8.24 (perfil suplementos): <pedido_suplementos> cria o pedido de varejo (variante sabor×peso
    // + estoque, decremento transacional → out_of_stock; gate de aceite humano; só entrega). Só age p/
    // profile_id='suplementos'.

    private final int maxAttempts;
    private final List<Duration> backoffs;

    // Nome do modelo Gemini vigente (mesma fonte de verdade que o GeminiProvider lê).
    // Gravado em messages.model junto dos tokens (6.2.5): verdade temporal — uma troca
    // de modelo futura preserva no histórico qual modelo gerou cada resposta.
    private final String geminiModel;

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
                           ObjectMapper objectMapper,
                           AppointmentService appointmentService,
                           AiSettingsRepository aiSettingsRepository,
                           com.meada.admin.health.ErrorLogger errorLogger,
                           com.meada.profiles.CompanyProfileRepository companyProfileRepository,
                           com.meada.profiles.sushi.orders.OrderConfirmHandler orderConfirmHandler,
                           @org.springframework.beans.factory.annotation.Value("${gemini.model}")
                           String geminiModel) {
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
        this.appointmentService = appointmentService;
        this.aiSettingsRepository = aiSettingsRepository;
        this.errorLogger = errorLogger;
        this.companyProfileRepository = companyProfileRepository;
        this.orderConfirmHandler = orderConfirmHandler;
        this.geminiModel = geminiModel;
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
                persistInsights(event.companyId(), conversationId, aiResponse);
                // O handoff NÃO roda a cadeia de handlers (a ação fica com o humano) — mas a tag
                // não pode vazar crua pro cliente: strip SEM agir. Se o reply era SÓ a tag, não
                // há resposta-ponte útil → cai no comportamento do caso 2 (flipa sem enviar).
                AiResponse bridge = stripLeftoverTags(aiResponse);
                if (bridge.reply() != null && !bridge.reply().isBlank()) {
                    Optional<OutboundOutcome> sendFailure = sendAndPersist(event, conversationId, bridge);
                    if (sendFailure.isPresent()) {
                        return sendFailure.get();   // falha de envio domina (casos 7/8/9) — já logado lá
                    }
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

        // caso 6: caminho feliz — boas-vindas (#82) na 1ª mensagem do contato (best-effort,
        // ANTES da resposta da IA), depois persiste a intent (#29) e os insights (5.18) e envia.
        maybeSendWelcome(event, conversationId);
        persistSchedulingIntent(conversationId, aiResponse);
        persistInsights(event.companyId(), conversationId, aiResponse);
        // Camada 7.1 (perfil sushi): pós-processa a tag <pedido> — cria o pedido e remove a tag
        // do texto antes de enviar. Só age para o perfil sushi; demais perfis seguem intactos.
        AiResponse toSend = maybeProcessSushiOrder(event, conversationId, aiResponse);
        // Camada 7.3 (perfil restaurant): pós-processa a tag <reserva> — cria a reserva e remove a
        // tag. Só age para o perfil restaurant. Encadeado após o sushi (perfil é único; só um age).
        // Camada 7.4 (perfil dental): pós-processa a tag <consulta> — cria a consulta e remove a tag.
        // Só age para o perfil dental. Encadeado (perfil é único; só um dos post-process age).
        // Camada 7.5 (perfil salon): pós-processa a tag <agendamento> — cria o agendamento e remove a tag.
        // Camada 7.6 (perfil pousada): pós-processa a tag <reserva_pousada> — cria a reserva e remove a tag.
        // Camada 7.7 (perfil academia): pós-processa a tag <matricula> — cria a matrícula e remove a tag.


        // Camada 8.1 (perfil barbearia): <agendamento_barbearia> marca horário; <fila_barbearia>
        // enfileira o cliente no walk-in. Encadeados (perfil é único; só um age).
        // Onda restaurant 1: <confirmacao_reserva> reflete o SIM/NÃO do lembrete de véspera.
        // Onda pousada 1: <confirmacao_pousada> reflete o SIM/cancelamento do lembrete de check-in.
        // Onda pet 1: <confirmacao_pet> reflete o SIM/desmarcar do lembrete de véspera.
        // Onda ótica 1: <confirmacao_exame> reflete o SIM/desmarcar do lembrete de exame.
        // Onda nutri 1: <confirmacao_nutri> reflete o SIM/desmarcar do lembrete de consulta.
        // Onda moda_infantil 1: <aviso_estoque_moda> registra o avise-me quando voltar.
        // Onda lingerie 1: <aviso_estoque_lingerie> registra o avise-me quando voltar.
        // Camada 8.2 (perfil eventos): <proposta_evento> abre a proposta; <aprovacao_proposta> muta o
        // estado da proposta orçada. Encadeados (perfil é único; só um age).
        // Camada 8.3 (perfil estetica): <agendamento_estetica> agenda (consome saldo de pacote);
        // <compra_pacote> registra a intenção de compra (pacote pendente). Encadeados (perfil único).
        // Camada 8.4 (perfil comida): <pedido_comida> cria o pedido de delivery (nasce 'aguardando' —
        // o restaurante aceita/recusa no painel, não a IA). Encadeado (perfil é único; só um age).
        // Camada 8.6 (perfil pizzaria): <pedido_pizza> cria o pedido (nasce 'aguardando'; aceite/recusa
        // é humano no painel). Inclui a escapada meio-a-meio. Encadeado (perfil é único; só um age).
        // Camada 8.9 (perfil adega): <pedido_adega> cria o pedido de bebidas (nasce 'aguardando';
        // trava +18 — sem age_confirmed=true não cria). Encadeado (perfil é único; só um age).
        // Camada 8.19 (perfil escola): <matricula_escola> (matrícula) e <visita_escola> (visita).
        // Encadeados após os demais (perfil é único; só um age). Removem a tag antes de enviar.
        // Camada 8.14 (perfil atelie): <proposta_atelie> abre proposta; <aprovacao_atelie> captura
        // aprovação/recusa (só 'orcada'). Encadeados após os demais (perfil único; só um age).
        // Camada 8.7 (perfil casamento): <proposta_casamento> abre; <aprovacao_casamento> captura
        // aprovação/recusa (só 'orcada'). Encadeados após os demais (perfil único; só um age).
        // Camada 8.17 (perfil concessionaria): <testdrive_carro> agenda; <lead_carro> registra interesse.
        // Encadeados após os demais (perfil único; só um age). Removem a tag antes de enviar.
        // Camada 8.10 (perfil lavanderia): <pedido_lavanderia> cria o pedido de lavagem (2 datas).
        // Camada 8.11 (perfil dermatologia): <consulta_derma> agenda; <entrega_preparo> entrega preparo.
        // Camada 8.16 (perfil fotografia): <sessao_foto> agenda; <entrega_material> entrega o link.
        // Camada 8.20 (perfil cursos): <matricula_curso> matricula; <entrega_modulo> entrega o módulo.
        // Camada 8.21 (perfil lingerie): <pedido_lingerie> cria o pedido de varejo (variante×estoque).
        // Camada 8.22 (perfil moda_infantil): <pedido_moda_infantil> cria o pedido de varejo infantil.
        // Camada 8.23 (perfil las): <pedido_las> cria o pedido de varejo de lãs.
        // Camada 8.8 (perfil padaria): <encomenda_padaria> cria o pedido de padaria.
        // Camada 8.12 (perfil otica): <exame_otica> agenda exame; <encomenda_otica> cria a encomenda.
        // Camada 8.15 (perfil papelaria): <pedido_papelaria> cria; <aprovacao_arte> aprova a arte.
        // Camada 8.18 (perfil viagens): <proposta_viagem> abre; <aprovacao_viagem> aprova/recusa.
        // Camada 8.24 (perfil suplementos): <pedido_suplementos> cria o pedido de varejo.
        // Rede de segurança: tag que NENHUM handler interpretou (perfil errado/alucinada) não
        // pode chegar crua ao cliente. Se o reply era SÓ a tag, não sobrou nada útil a enviar —
        // contrato quebrado, mesmo tratamento do caso 3 (flipa para humano).
        toSend = stripLeftoverTags(toSend);
        if (toSend.reply() == null || toSend.reply().isBlank()) {
            conversationRepository.markHandledByHuman(conversationId);
            return logOutcome(OutboundOutcome.FLIPPED_AI_BAD_REPLY, event, aiResponse, null);
        }
        Optional<OutboundOutcome> sendFailure = sendAndPersist(event, conversationId, toSend);
        if (sendFailure.isPresent()) {
            return sendFailure.get();   // casos 7/8/9 — já logado lá
        }
        return logOutcome(OutboundOutcome.PROCESSED, event, aiResponse, null);
    }

    /**
     * Remove do reply qualquer tag de ação que tenha SOBRADO sem interpretação (formato
     * canônico {@code <nome_tag>{json}</nome_tag>}). Acontece em dois cenários: (a) handoff
     * (needsHuman=true) — a cadeia de handlers não roda e a ação fica com o humano; (b) tag
     * de perfil errado/alucinada — o early-return de perfil devolve o reply intacto. Em ambos,
     * a tag é descartada (nenhuma entidade é criada) e o texto restante segue ao cliente.
     */
    private AiResponse stripLeftoverTags(AiResponse response) {
        String reply = response.reply();
        if (reply == null || reply.isBlank()) {
            return response;
        }
        Matcher matcher = LEFTOVER_TAG.matcher(reply);
        if (!matcher.find()) {
            return response;
        }
        String stripped = matcher.replaceAll("").trim();
        log.warn("outbound: leftover AI tag stripped before send (handoff ou tag de perfil errado; nenhuma ação executada)");
        return new AiResponse(stripped, response.needsHuman(), response.reason(),
            response.tokensIn(), response.tokensOut(), response.latencyMs(),
            response.schedulingIntent(), response.insights());
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
     * Boas-vindas (camada 5.21 #82): na PRIMEIRA mensagem do contato em todo o histórico,
     * envia a mensagem de boas-vindas configurada (ai_settings.welcome_message) ANTES da
     * resposta normal da IA. No-op (silencioso) quando não é a primeira mensagem OU o tenant
     * não configurou welcome_message — o caso da esmagadora maioria das mensagens.
     *
     * <p>Semântica de "primeira mensagem" (decisão cravada): conta as inbound do contato em
     * todas as suas conversas. O webhook persiste a inbound ANTES de disparar o evento, então
     * {@code count == 1} é a primeira de todas; tratamos {@code count <= 1} como primeira (também
     * cobre o fluxo direto de teste/sem pré-persistência). Só envia se houver welcome_message.
     *
     * <p>É best-effort e DEFENSIVO por contrato: qualquer falha (contato sumiu, lookup, envio,
     * persistência) é logada em warn e NUNCA propaga — a resposta da IA (o que importa para o
     * cliente) segue normalmente. Reusa o caminho de envio/persistência do caso 6 via um
     * {@link AiResponse} sintético (reply=welcome, needsHuman=false, métricas zeradas: não houve IA).
     * Diferente do sendAndPersist do reply, aqui IGNORAMOS o Optional de falha de envio: o welcome
     * não pode degradar o outcome do atendimento.
     */
    private void maybeSendWelcome(MessageInboundProcessedEvent event, UUID conversationId) {
        try {
            Optional<UUID> contactId =
                conversationRepository.findContactIdByConversation(conversationId);
            if (contactId.isEmpty()) {
                return;   // conversa sem contato resolúvel — nada a fazer (silencioso).
            }
            // Não é a 1ª mensagem do contato → sem boas-vindas (caso dominante).
            if (messageRepository.countInboundForContact(contactId.get()) > 1) {
                return;
            }
            Optional<String> welcome = aiSettingsRepository.findWelcomeMessage(event.companyId());
            if (welcome.isEmpty()) {
                return;   // tenant não configurou boas-vindas — comportamento invisível.
            }
            // Synthetic AiResponse (reply=welcome): reusa o sendAndPersist do caso 6. Ignoramos
            // o Optional de falha — o welcome é best-effort e não degrada o outcome do atendimento.
            AiResponse welcomeSynthetic = new AiResponse(welcome.get(), false, null, 0, 0, 0L);
            sendAndPersist(event, conversationId, welcomeSynthetic);
        } catch (RuntimeException e) {
            // Boas-vindas NUNCA derruba o atendimento (igual ao persistSchedulingIntent/insights).
            log.warn("outbound: failed to send welcome for conversation {} ({})",
                conversationId, e.getMessage());
        }
    }

    /**
     * Pós-processamento do perfil sushi (camada 7.1): se o tenant é sushi e a resposta da IA
     * contém a tag {@code <pedido>}, cria o pedido (OrderConfirmHandler) e devolve um AiResponse
     * com o texto SEM a tag (para não enviá-la ao cliente). Para qualquer outro perfil, ou sem
     * tag, devolve o aiResponse original inalterado.
     *
     * <p>Best-effort: falha em criar o pedido NÃO impede o envio da mensagem (o handler já loga e
     * retorna empty). A tag só é removida quando há tag — se o handler não criar pedido mas a tag
     * existir (item inválido), ainda assim removemos a tag (o cliente não pode ver JSON cru).
     */
    private AiResponse maybeProcessSushiOrder(MessageInboundProcessedEvent event,
                                              UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !orderConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;   // sem tag → caminho comum (maioria das mensagens).
        }
        if (!"sushi".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;   // tag num perfil não-sushi: não interpretamos (defensivo).
        }
        // Resolve o contato da conversa para criar o pedido.
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            orderConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        // Remove a tag do texto de qualquer forma (o cliente nunca vê o JSON), preservando as
        // métricas da IA (tokens/latency) num AiResponse equivalente.
        String stripped = orderConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
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
            // Erro alertável (camada 6.4): registra no error_log para a tela de erros do super-admin.
            errorLogger.log("OutboundService", e, java.util.Map.of(
                "conversationId", conversationId.toString(), "reason", "evolution_fatal"));
            return Optional.of(logOutcome(
                OutboundOutcome.EVOLUTION_CONFIG_ERROR, event, aiResponse, "evolution_fatal"));
        }

        // ---- BLOCO 4 — persiste a outbound (com tokens da IA — 6.2.5) ----
        // janela de crash: a mensagem JÁ foi enviada ao cliente; se o insert falhar,
        // logamos para reconciliação manual. insertIfNew é idempotente pelo evolution_message_id.
        //
        // tokens/model só quando HOUVE IA real: o reply sintético (boas-vindas, fora-de-horário)
        // constrói AiResponse com tokens 0/0 — gravamos NULL nesses casos, NÃO 0, para distinguir
        // "mensagem sem IA" de "IA com custo zero". O nome do modelo vem do config (geminiModel),
        // mesma fonte de verdade que o GeminiProvider — verdade temporal por resposta.
        boolean fromAi = aiResponse.tokensIn() > 0 || aiResponse.tokensOut() > 0;
        Integer tokensIn = fromAi ? aiResponse.tokensIn() : null;
        Integer tokensOut = fromAi ? aiResponse.tokensOut() : null;
        String model = fromAi ? geminiModel : null;
        Optional<?> inserted = messageRepository.insertIfNew(
            event.companyId(), conversationId,
            MessageDirection.OUTBOUND, MessageSender.AI, reply, keyId,
            tokensIn, tokensOut, model);
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
     * <p>#60/#64: quando há appointmentAction (book/reschedule/cancel), aplica a ação sobre
     * appointments via {@link AppointmentService#applyAppointmentAction} — que valida janela/
     * conflito e NUNCA lança (best-effort; o reply ao cliente já segue independente do agendamento).
     *
     * <p>Falha de persistência NÃO derruba o atendimento: logamos warn e seguimos (a resposta
     * ao cliente é mais importante que a marcação interna), igual ao persistSchedulingIntent.
     */
    private void persistInsights(UUID companyId, UUID conversationId, AiResponse aiResponse) {
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

            // #60/#64: ação de agendamento da IA (book/reschedule/cancel). applyAppointmentAction
            // valida janela/conflito e NUNCA lança — mas fica dentro do try por simetria/defesa.
            if (insights.appointmentAction() != null) {
                appointmentService.applyAppointmentAction(
                    companyId, conversationId, insights.appointmentAction());
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
