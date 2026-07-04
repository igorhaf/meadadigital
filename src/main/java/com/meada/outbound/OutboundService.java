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
    private final AppointmentService appointmentService;
    private final AiSettingsRepository aiSettingsRepository;
    private final com.meada.admin.health.ErrorLogger errorLogger;
    // Camada 7.1 (perfil sushi): pós-processa a resposta da IA para extrair a tag <pedido>,
    // criar o pedido e remover a tag antes de enviar ao cliente. Só age para profile_id='sushi'.
    private final com.meada.profiles.CompanyProfileRepository companyProfileRepository;
    private final com.meada.profiles.sushi.orders.OrderConfirmHandler orderConfirmHandler;
    // Camada 7.3 (perfil restaurant): pós-processa a tag <reserva> — cria a reserva e remove a tag.
    private final com.meada.profiles.restaurant.reservations.ReservationConfirmHandler reservationConfirmHandler;
    // Camada 7.4 (perfil dental): pós-processa a tag <consulta> — cria a consulta e remove a tag.
    private final com.meada.profiles.dental.appointments.ConsultaConfirmHandler consultaConfirmHandler;
    // Camada 7.5 (perfil salon): pós-processa a tag <agendamento> — cria o agendamento e remove a tag.
    private final com.meada.profiles.salon.appointments.AgendamentoConfirmHandler agendamentoConfirmHandler;
    // Camada 7.6 (perfil pousada): pós-processa a tag <reserva_pousada> — cria a reserva e remove a tag.
    private final com.meada.profiles.pousada.reservations.ReservaPousadaConfirmHandler reservaPousadaConfirmHandler;
    // Camada 7.7 (perfil academia): pós-processa a tag <matricula> — cria a matrícula e remove a tag.
    private final com.meada.profiles.academia.memberships.MatriculaConfirmHandler matriculaConfirmHandler;
    private final com.meada.profiles.pet.appointments.AgendamentoPetConfirmHandler agendamentoPetConfirmHandler;
    // Camada 7.9 (perfil oficina): pós-processa <ordem_servico> (abre OS) e <aprovacao_os> (muta estado).
    private final com.meada.profiles.oficina.orders.AberturaOsConfirmHandler aberturaOsConfirmHandler;
    private final com.meada.profiles.oficina.orders.AprovacaoOsHandler aprovacaoOsHandler;
    // Camada 8.0 (perfil nutri): pós-processa <consulta_nutri> (agenda) e <entrega_plano> (entrega o body exato).
    private final com.meada.profiles.nutri.appointments.AgendamentoNutriConfirmHandler agendamentoNutriConfirmHandler;
    private final com.meada.profiles.nutri.appointments.EntregaPlanoHandler entregaPlanoHandler;
    // Camada 8.1 (perfil barbearia): pós-processa <agendamento_barbearia> (marca), <fila_barbearia>
    // (enfileira) e <confirmacao_barbearia> (o cliente confirma/cancela o horário — onda 1 do backlog #1).
    private final com.meada.profiles.barbearia.appointments.AgendamentoBarbeariaConfirmHandler agendamentoBarbeariaConfirmHandler;
    private final com.meada.profiles.barbearia.queue.EntrarFilaHandler entrarFilaHandler;
    private final com.meada.profiles.barbearia.appointments.ConfirmacaoBarbeariaHandler confirmacaoBarbeariaHandler;
    private final com.meada.profiles.restaurant.reservations.ConfirmacaoReservaHandler confirmacaoReservaHandler;
    private final com.meada.profiles.pousada.reservations.ConfirmacaoPousadaHandler confirmacaoPousadaHandler;
    private final com.meada.profiles.pet.appointments.ConfirmacaoPetHandler confirmacaoPetHandler;
    // Camada 8.2 (perfil eventos): pós-processa <proposta_evento> (abre proposta) e <aprovacao_proposta> (muta estado).
    private final com.meada.profiles.eventos.proposals.PropostaEventoConfirmHandler propostaEventoConfirmHandler;
    private final com.meada.profiles.eventos.proposals.AprovacaoPropostaHandler aprovacaoPropostaHandler;
    // Camada 8.3 (perfil estetica): pós-processa <agendamento_estetica> (agenda, consome saldo) e <compra_pacote> (cria pacote pendente).
    private final com.meada.profiles.estetica.appointments.AgendamentoEsteticaConfirmHandler agendamentoEsteticaConfirmHandler;
    private final com.meada.profiles.estetica.packages.CompraPacoteConfirmHandler compraPacoteConfirmHandler;
    // Camada 8.4 (perfil comida): pós-processa a tag <pedido_comida> — cria o pedido (nasce 'aguardando')
    // e remove a tag antes de enviar ao cliente. Só age para profile_id='comida'. O aceite/recusa é
    // ação HUMANA no painel (não há handler de aceite — é o ponto central da ESCAPADA 1 do delivery).
    private final com.meada.profiles.comida.orders.PedidoComidaConfirmHandler pedidoComidaConfirmHandler;
    private final com.meada.profiles.floricultura.orders.PedidoFlorConfirmHandler pedidoFlorConfirmHandler;
    // Camada 8.6 (perfil pizzaria): pós-processa a tag <pedido_pizza> — cria o pedido (nasce 'aguardando')
    // e remove a tag antes de enviar. Só age para profile_id='pizzaria'. Inclui a ESCAPADA meio-a-meio
    // (frações de sabor, preço pela regra do MAIOR VALOR — recalculado no backend).
    private final com.meada.profiles.pizzaria.orders.PedidoPizzaConfirmHandler pedidoPizzaConfirmHandler;
    // Camada 8.9 (perfil adega): pós-processa a tag <pedido_adega> — cria o pedido (nasce 'aguardando')
    // e remove a tag antes de enviar. Só age para profile_id='adega'. Inclui a ESCAPADA +18 (a tag sem
    // age_confirmed=true não cria pedido — trava de faixa etária na venda de álcool).
    private final com.meada.profiles.adega.orders.PedidoAdegaConfirmHandler pedidoAdegaConfirmHandler;
    // Camada 8.19 (perfil escola): pós-processa <matricula_escola> (matrícula-assinatura, 2 modos:
    // student_id OU new_student) e <visita_escola> (agenda leve dia+período). Só agem p/ profile_id='escola'.
    private final com.meada.profiles.escola.enrollments.MatriculaEscolaConfirmHandler matriculaEscolaConfirmHandler;
    private final com.meada.profiles.escola.visits.VisitaEscolaConfirmHandler visitaEscolaConfirmHandler;
    // Camada 8.14 (perfil atelie): <proposta_atelie> ABRE a proposta (rascunho); <aprovacao_atelie> MUTA
    // o estado (aprovada/recusada, só se 'orcada') — gate de aprovação em 2 fases. Só agem p/ profile_id='atelie'.
    private final com.meada.profiles.atelie.proposals.PropostaAtelieConfirmHandler propostaAtelieConfirmHandler;
    private final com.meada.profiles.atelie.proposals.AprovacaoAtelieHandler aprovacaoAtelieHandler;
    // Camada 8.7 (perfil casamento): <proposta_casamento> ABRE a proposta (rascunho); <aprovacao_casamento>
    // MUTA o estado (aprovada/recusada, só se 'orcada') — gate de aprovação em 2 fases. Só p/ profile_id='casamento'.
    private final com.meada.profiles.casamento.proposals.PropostaCasamentoConfirmHandler propostaCasamentoConfirmHandler;
    private final com.meada.profiles.casamento.proposals.AprovacaoCasamentoHandler aprovacaoCasamentoHandler;
    // Camada 8.17 (perfil concessionaria): <testdrive_carro> agenda test-drive (conflito por vendedor);
    // <lead_carro> registra interesse de compra (funil). Só agem p/ profile_id='concessionaria'.
    private final com.meada.profiles.concessionaria.testdrives.TestDriveConfirmHandler testDriveConfirmHandler;
    private final com.meada.profiles.concessionaria.leads.LeadCarroConfirmHandler leadCarroConfirmHandler;
    // Onda 1 do backlog concessionária: <desejo_carro> registra a lista de desejos (#1) e
    // <confirmacao_testdrive> fecha o loop do lembrete SIM/CANCELAR (#3).
    private final com.meada.profiles.concessionaria.wishlists.DesejoCarroConfirmHandler desejoCarroConfirmHandler;
    private final com.meada.profiles.concessionaria.testdrives.ConfirmacaoTestDriveHandler confirmacaoTestDriveHandler;
    // Camada 8.10 (perfil lavanderia): <pedido_lavanderia> cria o pedido (nasce 'aguardando'; 2 datas
    // coleta+entrega com turnaround). Só age p/ profile_id='lavanderia'.
    private final com.meada.profiles.lavanderia.orders.PedidoLavanderiaConfirmHandler pedidoLavanderiaConfirmHandler;
    // Camada 8.11 (perfil dermatologia): <consulta_derma> agenda consulta (conflito por profissional,
    // 2 modos paciente); <entrega_preparo> entrega READ-ONLY a nota de preparo do tipo (verbatim, barreira
    // de contato). Só agem p/ profile_id='dermatologia'.
    private final com.meada.profiles.dermatologia.appointments.AgendamentoDermaConfirmHandler agendamentoDermaConfirmHandler;
    private final com.meada.profiles.dermatologia.appointments.EntregaPreparoHandler entregaPreparoHandler;
    // Camada 8.16 (perfil fotografia): <sessao_foto> agenda a sessão (conflito por profissional,
    // snapshot de pacote, end_at + delivery_due_date materializados); <entrega_material> entrega
    // READ-ONLY o link do material (barreira de contato). Só agem p/ profile_id='fotografia'.
    private final com.meada.profiles.fotografia.appointments.SessaoFotoConfirmHandler sessaoFotoConfirmHandler;
    private final com.meada.profiles.fotografia.appointments.EntregaMaterialHandler entregaMaterialHandler;
    // Camada 8.20 (perfil cursos): <matricula_curso> matricula (assinatura, anti-dupla por curso);
    // <entrega_modulo> entrega READ-ONLY o conteúdo do próximo módulo (barreira de contato + grava
    // progresso). Só agem p/ profile_id='cursos'.
    private final com.meada.profiles.cursos.enrollments.MatriculaCursoConfirmHandler matriculaCursoConfirmHandler;
    private final com.meada.profiles.cursos.enrollments.EntregaModuloHandler entregaModuloHandler;
    // Camada 8.21 (perfil lingerie): <pedido_lingerie> cria o pedido de varejo (variante×estoque,
    // decremento transacional → out_of_stock aborta; gate de aceite humano). Só age p/ profile_id='lingerie'.
    private final com.meada.profiles.lingerie.orders.PedidoLingerieConfirmHandler pedidoLingerieConfirmHandler;
    // Camada 8.22 (perfil moda_infantil): <pedido_moda_infantil> cria o pedido de varejo infantil
    // (variante faixa-etária×cor + estoque, decremento transacional, restock no cancelamento). Só age
    // p/ profile_id='moda_infantil'.
    private final com.meada.profiles.modainfantil.orders.PedidoModaInfantilConfirmHandler pedidoModaInfantilConfirmHandler;
    // Camada 8.23 (perfil las): <pedido_las> cria o pedido de varejo de lãs (variante cor×dye_lot +
    // estoque, decremento transacional, regra same_lot_guaranteed). Só age p/ profile_id='las'.
    private final com.meada.profiles.las.orders.PedidoLasConfirmHandler pedidoLasConfirmHandler;
    // Camada 8.8 (perfil padaria): <encomenda_padaria> cria o pedido de padaria/confeitaria (cardápio +
    // personalização + made_to_order/lead time + fulfillment + gate de aceite). Só age p/ profile_id='padaria'.
    private final com.meada.profiles.padaria.orders.EncomendaPadariaConfirmHandler encomendaPadariaConfirmHandler;
    // Camada 8.12 (perfil otica — HÍBRIDO): <exame_otica> agenda o exame (conflito por profissional);
    // <encomenda_otica> cria a encomenda de óculos (receita administrativa + lead time + gate de aceite).
    // Só agem p/ profile_id='otica'.
    private final com.meada.profiles.otica.appointments.ExameOticaConfirmHandler exameOticaConfirmHandler;
    private final com.meada.profiles.otica.orders.EncomendaOticaConfirmHandler encomendaOticaConfirmHandler;
    // Camada 8.15 (perfil papelaria): <pedido_papelaria> cria a encomenda gráfica (tiragem +
    // personalização + lead + gate de aceite); <aprovacao_arte> CAPTURA a aprovação da arte do cliente
    // (muta o estado de um pedido existente em 'arte_aprovacao'). Só agem p/ profile_id='papelaria'.
    private final com.meada.profiles.papelaria.orders.PedidoPapelariaConfirmHandler pedidoPapelariaConfirmHandler;
    private final com.meada.profiles.papelaria.orders.AprovacaoArteHandler aprovacaoArteHandler;
    // Camada 8.18 (perfil viagens): <proposta_viagem> ABRE a proposta de viagem (rascunho);
    // <aprovacao_viagem> CAPTURA a aprovação/recusa (muta o estado de uma proposta 'orcada' existente).
    // Só agem p/ profile_id='viagens'.
    private final com.meada.profiles.viagens.proposals.PropostaViagemConfirmHandler propostaViagemConfirmHandler;
    private final com.meada.profiles.viagens.proposals.AprovacaoViagemHandler aprovacaoViagemHandler;
    // Camada 8.24 (perfil suplementos): <pedido_suplementos> cria o pedido de varejo (variante sabor×peso
    // + estoque, decremento transacional → out_of_stock; gate de aceite humano; só entrega). Só age p/
    // profile_id='suplementos'.
    private final com.meada.profiles.suplementos.orders.PedidoSuplementosConfirmHandler pedidoSuplementosConfirmHandler;

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
                           com.meada.profiles.restaurant.reservations.ReservationConfirmHandler reservationConfirmHandler,
                           com.meada.profiles.dental.appointments.ConsultaConfirmHandler consultaConfirmHandler,
                           com.meada.profiles.salon.appointments.AgendamentoConfirmHandler agendamentoConfirmHandler,
                           com.meada.profiles.pousada.reservations.ReservaPousadaConfirmHandler reservaPousadaConfirmHandler,
                           com.meada.profiles.academia.memberships.MatriculaConfirmHandler matriculaConfirmHandler,
                           com.meada.profiles.pet.appointments.AgendamentoPetConfirmHandler agendamentoPetConfirmHandler,
                           com.meada.profiles.oficina.orders.AberturaOsConfirmHandler aberturaOsConfirmHandler,
                           com.meada.profiles.oficina.orders.AprovacaoOsHandler aprovacaoOsHandler,
                           com.meada.profiles.nutri.appointments.AgendamentoNutriConfirmHandler agendamentoNutriConfirmHandler,
                           com.meada.profiles.nutri.appointments.EntregaPlanoHandler entregaPlanoHandler,
                           com.meada.profiles.barbearia.appointments.AgendamentoBarbeariaConfirmHandler agendamentoBarbeariaConfirmHandler,
                           com.meada.profiles.barbearia.queue.EntrarFilaHandler entrarFilaHandler,
                           com.meada.profiles.barbearia.appointments.ConfirmacaoBarbeariaHandler confirmacaoBarbeariaHandler,
                           com.meada.profiles.restaurant.reservations.ConfirmacaoReservaHandler confirmacaoReservaHandler,
                           com.meada.profiles.pousada.reservations.ConfirmacaoPousadaHandler confirmacaoPousadaHandler,
                           com.meada.profiles.pet.appointments.ConfirmacaoPetHandler confirmacaoPetHandler,
                           com.meada.profiles.eventos.proposals.PropostaEventoConfirmHandler propostaEventoConfirmHandler,
                           com.meada.profiles.eventos.proposals.AprovacaoPropostaHandler aprovacaoPropostaHandler,
                           com.meada.profiles.estetica.appointments.AgendamentoEsteticaConfirmHandler agendamentoEsteticaConfirmHandler,
                           com.meada.profiles.estetica.packages.CompraPacoteConfirmHandler compraPacoteConfirmHandler,
                           com.meada.profiles.comida.orders.PedidoComidaConfirmHandler pedidoComidaConfirmHandler,
                           com.meada.profiles.floricultura.orders.PedidoFlorConfirmHandler pedidoFlorConfirmHandler,
                           com.meada.profiles.pizzaria.orders.PedidoPizzaConfirmHandler pedidoPizzaConfirmHandler,
                           com.meada.profiles.adega.orders.PedidoAdegaConfirmHandler pedidoAdegaConfirmHandler,
                           com.meada.profiles.escola.enrollments.MatriculaEscolaConfirmHandler matriculaEscolaConfirmHandler,
                           com.meada.profiles.escola.visits.VisitaEscolaConfirmHandler visitaEscolaConfirmHandler,
                           com.meada.profiles.atelie.proposals.PropostaAtelieConfirmHandler propostaAtelieConfirmHandler,
                           com.meada.profiles.atelie.proposals.AprovacaoAtelieHandler aprovacaoAtelieHandler,
                           com.meada.profiles.casamento.proposals.PropostaCasamentoConfirmHandler propostaCasamentoConfirmHandler,
                           com.meada.profiles.casamento.proposals.AprovacaoCasamentoHandler aprovacaoCasamentoHandler,
                           com.meada.profiles.concessionaria.testdrives.TestDriveConfirmHandler testDriveConfirmHandler,
                           com.meada.profiles.concessionaria.leads.LeadCarroConfirmHandler leadCarroConfirmHandler,
                           com.meada.profiles.concessionaria.wishlists.DesejoCarroConfirmHandler desejoCarroConfirmHandler,
                           com.meada.profiles.concessionaria.testdrives.ConfirmacaoTestDriveHandler confirmacaoTestDriveHandler,
                           com.meada.profiles.lavanderia.orders.PedidoLavanderiaConfirmHandler pedidoLavanderiaConfirmHandler,
                           com.meada.profiles.dermatologia.appointments.AgendamentoDermaConfirmHandler agendamentoDermaConfirmHandler,
                           com.meada.profiles.dermatologia.appointments.EntregaPreparoHandler entregaPreparoHandler,
                           com.meada.profiles.fotografia.appointments.SessaoFotoConfirmHandler sessaoFotoConfirmHandler,
                           com.meada.profiles.fotografia.appointments.EntregaMaterialHandler entregaMaterialHandler,
                           com.meada.profiles.cursos.enrollments.MatriculaCursoConfirmHandler matriculaCursoConfirmHandler,
                           com.meada.profiles.cursos.enrollments.EntregaModuloHandler entregaModuloHandler,
                           com.meada.profiles.lingerie.orders.PedidoLingerieConfirmHandler pedidoLingerieConfirmHandler,
                           com.meada.profiles.modainfantil.orders.PedidoModaInfantilConfirmHandler pedidoModaInfantilConfirmHandler,
                           com.meada.profiles.las.orders.PedidoLasConfirmHandler pedidoLasConfirmHandler,
                           com.meada.profiles.padaria.orders.EncomendaPadariaConfirmHandler encomendaPadariaConfirmHandler,
                           com.meada.profiles.otica.appointments.ExameOticaConfirmHandler exameOticaConfirmHandler,
                           com.meada.profiles.otica.orders.EncomendaOticaConfirmHandler encomendaOticaConfirmHandler,
                           com.meada.profiles.papelaria.orders.PedidoPapelariaConfirmHandler pedidoPapelariaConfirmHandler,
                           com.meada.profiles.papelaria.orders.AprovacaoArteHandler aprovacaoArteHandler,
                           com.meada.profiles.viagens.proposals.PropostaViagemConfirmHandler propostaViagemConfirmHandler,
                           com.meada.profiles.viagens.proposals.AprovacaoViagemHandler aprovacaoViagemHandler,
                           com.meada.profiles.suplementos.orders.PedidoSuplementosConfirmHandler pedidoSuplementosConfirmHandler,
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
        this.reservationConfirmHandler = reservationConfirmHandler;
        this.consultaConfirmHandler = consultaConfirmHandler;
        this.agendamentoConfirmHandler = agendamentoConfirmHandler;
        this.reservaPousadaConfirmHandler = reservaPousadaConfirmHandler;
        this.matriculaConfirmHandler = matriculaConfirmHandler;
        this.agendamentoPetConfirmHandler = agendamentoPetConfirmHandler;
        this.aberturaOsConfirmHandler = aberturaOsConfirmHandler;
        this.aprovacaoOsHandler = aprovacaoOsHandler;
        this.agendamentoNutriConfirmHandler = agendamentoNutriConfirmHandler;
        this.entregaPlanoHandler = entregaPlanoHandler;
        this.agendamentoBarbeariaConfirmHandler = agendamentoBarbeariaConfirmHandler;
        this.entrarFilaHandler = entrarFilaHandler;
        this.confirmacaoBarbeariaHandler = confirmacaoBarbeariaHandler;
        this.confirmacaoReservaHandler = confirmacaoReservaHandler;
        this.confirmacaoPousadaHandler = confirmacaoPousadaHandler;
        this.confirmacaoPetHandler = confirmacaoPetHandler;
        this.propostaEventoConfirmHandler = propostaEventoConfirmHandler;
        this.aprovacaoPropostaHandler = aprovacaoPropostaHandler;
        this.agendamentoEsteticaConfirmHandler = agendamentoEsteticaConfirmHandler;
        this.compraPacoteConfirmHandler = compraPacoteConfirmHandler;
        this.pedidoComidaConfirmHandler = pedidoComidaConfirmHandler;
        this.pedidoFlorConfirmHandler = pedidoFlorConfirmHandler;
        this.pedidoPizzaConfirmHandler = pedidoPizzaConfirmHandler;
        this.pedidoAdegaConfirmHandler = pedidoAdegaConfirmHandler;
        this.matriculaEscolaConfirmHandler = matriculaEscolaConfirmHandler;
        this.visitaEscolaConfirmHandler = visitaEscolaConfirmHandler;
        this.propostaAtelieConfirmHandler = propostaAtelieConfirmHandler;
        this.aprovacaoAtelieHandler = aprovacaoAtelieHandler;
        this.propostaCasamentoConfirmHandler = propostaCasamentoConfirmHandler;
        this.aprovacaoCasamentoHandler = aprovacaoCasamentoHandler;
        this.testDriveConfirmHandler = testDriveConfirmHandler;
        this.leadCarroConfirmHandler = leadCarroConfirmHandler;
        this.desejoCarroConfirmHandler = desejoCarroConfirmHandler;
        this.confirmacaoTestDriveHandler = confirmacaoTestDriveHandler;
        this.pedidoLavanderiaConfirmHandler = pedidoLavanderiaConfirmHandler;
        this.agendamentoDermaConfirmHandler = agendamentoDermaConfirmHandler;
        this.entregaPreparoHandler = entregaPreparoHandler;
        this.sessaoFotoConfirmHandler = sessaoFotoConfirmHandler;
        this.entregaMaterialHandler = entregaMaterialHandler;
        this.matriculaCursoConfirmHandler = matriculaCursoConfirmHandler;
        this.entregaModuloHandler = entregaModuloHandler;
        this.pedidoLingerieConfirmHandler = pedidoLingerieConfirmHandler;
        this.pedidoModaInfantilConfirmHandler = pedidoModaInfantilConfirmHandler;
        this.pedidoLasConfirmHandler = pedidoLasConfirmHandler;
        this.encomendaPadariaConfirmHandler = encomendaPadariaConfirmHandler;
        this.exameOticaConfirmHandler = exameOticaConfirmHandler;
        this.encomendaOticaConfirmHandler = encomendaOticaConfirmHandler;
        this.pedidoPapelariaConfirmHandler = pedidoPapelariaConfirmHandler;
        this.aprovacaoArteHandler = aprovacaoArteHandler;
        this.propostaViagemConfirmHandler = propostaViagemConfirmHandler;
        this.aprovacaoViagemHandler = aprovacaoViagemHandler;
        this.pedidoSuplementosConfirmHandler = pedidoSuplementosConfirmHandler;
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
        toSend = maybeProcessRestaurantReservation(event, conversationId, toSend);
        // Camada 7.4 (perfil dental): pós-processa a tag <consulta> — cria a consulta e remove a tag.
        // Só age para o perfil dental. Encadeado (perfil é único; só um dos post-process age).
        toSend = maybeProcessDentalAppointment(event, conversationId, toSend);
        // Camada 7.5 (perfil salon): pós-processa a tag <agendamento> — cria o agendamento e remove a tag.
        toSend = maybeProcessSalonAppointment(event, conversationId, toSend);
        // Camada 7.6 (perfil pousada): pós-processa a tag <reserva_pousada> — cria a reserva e remove a tag.
        toSend = maybeProcessPousadaReservation(event, conversationId, toSend);
        // Camada 7.7 (perfil academia): pós-processa a tag <matricula> — cria a matrícula e remove a tag.
        toSend = maybeProcessMatricula(event, conversationId, toSend);

        toSend = maybeProcessPetAppointment(event, conversationId, toSend);

        toSend = maybeProcessAberturaOs(event, conversationId, toSend);
        toSend = maybeProcessAprovacaoOs(event, conversationId, toSend);

        toSend = maybeProcessConsultaNutri(event, conversationId, toSend);
        toSend = maybeProcessEntregaPlano(event, conversationId, toSend);
        // Camada 8.1 (perfil barbearia): <agendamento_barbearia> marca horário; <fila_barbearia>
        // enfileira o cliente no walk-in. Encadeados (perfil é único; só um age).
        toSend = maybeProcessAgendamentoBarbearia(event, conversationId, toSend);
        toSend = maybeProcessFilaBarbearia(event, conversationId, toSend);
        toSend = maybeProcessConfirmacaoBarbearia(event, conversationId, toSend);
        // Onda restaurant 1: <confirmacao_reserva> reflete o SIM/NÃO do lembrete de véspera.
        toSend = maybeProcessConfirmacaoReserva(event, conversationId, toSend);
        // Onda pousada 1: <confirmacao_pousada> reflete o SIM/cancelamento do lembrete de check-in.
        toSend = maybeProcessConfirmacaoPousada(event, conversationId, toSend);
        // Onda pet 1: <confirmacao_pet> reflete o SIM/desmarcar do lembrete de véspera.
        toSend = maybeProcessConfirmacaoPet(event, conversationId, toSend);
        // Camada 8.2 (perfil eventos): <proposta_evento> abre a proposta; <aprovacao_proposta> muta o
        // estado da proposta orçada. Encadeados (perfil é único; só um age).
        toSend = maybeProcessPropostaEvento(event, conversationId, toSend);
        toSend = maybeProcessAprovacaoProposta(event, conversationId, toSend);
        // Camada 8.3 (perfil estetica): <agendamento_estetica> agenda (consome saldo de pacote);
        // <compra_pacote> registra a intenção de compra (pacote pendente). Encadeados (perfil único).
        toSend = maybeProcessAgendamentoEstetica(event, conversationId, toSend);
        toSend = maybeProcessCompraPacote(event, conversationId, toSend);
        // Camada 8.4 (perfil comida): <pedido_comida> cria o pedido de delivery (nasce 'aguardando' —
        // o restaurante aceita/recusa no painel, não a IA). Encadeado (perfil é único; só um age).
        toSend = maybeProcessPedidoComida(event, conversationId, toSend);
        toSend = maybeProcessPedidoFlor(event, conversationId, toSend);
        // Camada 8.6 (perfil pizzaria): <pedido_pizza> cria o pedido (nasce 'aguardando'; aceite/recusa
        // é humano no painel). Inclui a escapada meio-a-meio. Encadeado (perfil é único; só um age).
        toSend = maybeProcessPedidoPizza(event, conversationId, toSend);
        // Camada 8.9 (perfil adega): <pedido_adega> cria o pedido de bebidas (nasce 'aguardando';
        // trava +18 — sem age_confirmed=true não cria). Encadeado (perfil é único; só um age).
        toSend = maybeProcessPedidoAdega(event, conversationId, toSend);
        // Camada 8.19 (perfil escola): <matricula_escola> (matrícula) e <visita_escola> (visita).
        // Encadeados após os demais (perfil é único; só um age). Removem a tag antes de enviar.
        toSend = maybeProcessMatriculaEscola(event, conversationId, toSend);
        toSend = maybeProcessVisitaEscola(event, conversationId, toSend);
        // Camada 8.14 (perfil atelie): <proposta_atelie> abre proposta; <aprovacao_atelie> captura
        // aprovação/recusa (só 'orcada'). Encadeados após os demais (perfil único; só um age).
        toSend = maybeProcessPropostaAtelie(event, conversationId, toSend);
        toSend = maybeProcessAprovacaoAtelie(event, conversationId, toSend);
        // Camada 8.7 (perfil casamento): <proposta_casamento> abre; <aprovacao_casamento> captura
        // aprovação/recusa (só 'orcada'). Encadeados após os demais (perfil único; só um age).
        toSend = maybeProcessPropostaCasamento(event, conversationId, toSend);
        toSend = maybeProcessAprovacaoCasamento(event, conversationId, toSend);
        // Camada 8.17 (perfil concessionaria): <testdrive_carro> agenda; <lead_carro> registra interesse.
        // Encadeados após os demais (perfil único; só um age). Removem a tag antes de enviar.
        toSend = maybeProcessTestDrive(event, conversationId, toSend);
        toSend = maybeProcessLeadCarro(event, conversationId, toSend);
        toSend = maybeProcessDesejoCarro(event, conversationId, toSend);
        toSend = maybeProcessConfirmacaoTestDrive(event, conversationId, toSend);
        // Camada 8.10 (perfil lavanderia): <pedido_lavanderia> cria o pedido de lavagem (2 datas).
        toSend = maybeProcessPedidoLavanderia(event, conversationId, toSend);
        // Camada 8.11 (perfil dermatologia): <consulta_derma> agenda; <entrega_preparo> entrega preparo.
        toSend = maybeProcessConsultaDerma(event, conversationId, toSend);
        toSend = maybeProcessEntregaPreparo(event, conversationId, toSend);
        // Camada 8.16 (perfil fotografia): <sessao_foto> agenda; <entrega_material> entrega o link.
        toSend = maybeProcessSessaoFoto(event, conversationId, toSend);
        toSend = maybeProcessEntregaMaterial(event, conversationId, toSend);
        // Camada 8.20 (perfil cursos): <matricula_curso> matricula; <entrega_modulo> entrega o módulo.
        toSend = maybeProcessMatriculaCurso(event, conversationId, toSend);
        toSend = maybeProcessEntregaModulo(event, conversationId, toSend);
        // Camada 8.21 (perfil lingerie): <pedido_lingerie> cria o pedido de varejo (variante×estoque).
        toSend = maybeProcessPedidoLingerie(event, conversationId, toSend);
        // Camada 8.22 (perfil moda_infantil): <pedido_moda_infantil> cria o pedido de varejo infantil.
        toSend = maybeProcessPedidoModaInfantil(event, conversationId, toSend);
        // Camada 8.23 (perfil las): <pedido_las> cria o pedido de varejo de lãs.
        toSend = maybeProcessPedidoLas(event, conversationId, toSend);
        // Camada 8.8 (perfil padaria): <encomenda_padaria> cria o pedido de padaria.
        toSend = maybeProcessEncomendaPadaria(event, conversationId, toSend);
        // Camada 8.12 (perfil otica): <exame_otica> agenda exame; <encomenda_otica> cria a encomenda.
        toSend = maybeProcessExameOtica(event, conversationId, toSend);
        toSend = maybeProcessEncomendaOtica(event, conversationId, toSend);
        // Camada 8.15 (perfil papelaria): <pedido_papelaria> cria; <aprovacao_arte> aprova a arte.
        toSend = maybeProcessPedidoPapelaria(event, conversationId, toSend);
        toSend = maybeProcessAprovacaoArte(event, conversationId, toSend);
        // Camada 8.18 (perfil viagens): <proposta_viagem> abre; <aprovacao_viagem> aprova/recusa.
        toSend = maybeProcessPropostaViagem(event, conversationId, toSend);
        toSend = maybeProcessAprovacaoViagem(event, conversationId, toSend);
        // Camada 8.24 (perfil suplementos): <pedido_suplementos> cria o pedido de varejo.
        toSend = maybeProcessPedidoSuplementos(event, conversationId, toSend);
        Optional<OutboundOutcome> sendFailure = sendAndPersist(event, conversationId, toSend);
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
     * Pós-processamento do perfil restaurant (camada 7.3): se o tenant é restaurant e a resposta da
     * IA contém a tag {@code <reserva>}, cria a reserva (ReservationConfirmHandler) e devolve um
     * AiResponse com o texto SEM a tag. Para qualquer outro perfil, ou sem tag, devolve o aiResponse
     * original inalterado. Espelho de {@link #maybeProcessSushiOrder}.
     *
     * <p>Best-effort: falha em criar a reserva (conflito de slot, fora do horário, mesa inválida)
     * NÃO impede o envio da mensagem (o handler já loga e retorna empty). A tag é removida sempre
     * que existir — o cliente não pode ver JSON cru.
     */
    private AiResponse maybeProcessRestaurantReservation(MessageInboundProcessedEvent event,
                                                         UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !reservationConfirmHandler.hasReservationTag(reply)) {
            return aiResponse;   // sem tag → caminho comum (maioria das mensagens).
        }
        if (!"restaurant".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;   // tag num perfil não-restaurant: não interpretamos (defensivo).
        }
        // Resolve contato (id + nome + telefone) da conversa para criar a reserva (guest snapshot).
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            String guestName = contactRepository.findNameByConversationId(conversationId)
                .filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> contactRepository.findPhoneByConversationId(conversationId).orElse("Cliente"));
            String guestPhone = contactRepository.findPhoneByConversationId(conversationId).orElse(null);
            reservationConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), guestName, guestPhone, reply);
        }
        // Remove a tag do texto de qualquer forma (o cliente nunca vê o JSON), preservando métricas.
        String stripped = reservationConfirmHandler.stripReservationTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil dental (camada 7.4): se o tenant é dental e a resposta da IA
     * contém a tag {@code <consulta>}, cria a consulta (ConsultaConfirmHandler resolve o paciente
     * pelo contato) e devolve um AiResponse com o texto SEM a tag. Para outro perfil, ou sem tag,
     * devolve o aiResponse original. Espelho de {@link #maybeProcessRestaurantReservation}.
     *
     * <p>Best-effort: falha em criar a consulta (paciente não identificado, conflito, fora do
     * horário) NÃO impede o envio (o handler loga e retorna empty). A tag é removida sempre que
     * existir — o paciente não vê JSON cru.
     */
    private AiResponse maybeProcessDentalAppointment(MessageInboundProcessedEvent event,
                                                     UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !consultaConfirmHandler.hasConsultaTag(reply)) {
            return aiResponse;   // sem tag → caminho comum.
        }
        if (!"dental".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;   // tag num perfil não-dental: não interpretamos (defensivo).
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            consultaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = consultaConfirmHandler.stripConsultaTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil salon (camada 7.5): se o tenant é salon e a resposta da IA contém
     * a tag {@code <agendamento>}, cria o agendamento (AgendamentoConfirmHandler resolve o contato) e
     * devolve um AiResponse SEM a tag. Para outro perfil, ou sem tag, devolve o original. Espelho de
     * {@link #maybeProcessDentalAppointment}. Best-effort.
     */
    private AiResponse maybeProcessSalonAppointment(MessageInboundProcessedEvent event,
                                                    UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !agendamentoConfirmHandler.hasAgendamentoTag(reply)) {
            return aiResponse;
        }
        if (!"salon".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            agendamentoConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = agendamentoConfirmHandler.stripAgendamentoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil pousada (camada 7.6): se o tenant é pousada e a resposta da IA
     * contém a tag {@code <reserva_pousada>}, cria a reserva (ReservaPousadaConfirmHandler resolve o
     * contato) e devolve um AiResponse SEM a tag. Para outro perfil, ou sem tag, devolve o original.
     * Espelho de {@link #maybeProcessSalonAppointment}. Best-effort. Tag distinta de {@code <reserva>}
     * do RestaurantBot.
     */
    private AiResponse maybeProcessPousadaReservation(MessageInboundProcessedEvent event,
                                                      UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !reservaPousadaConfirmHandler.hasReservaPousadaTag(reply)) {
            return aiResponse;
        }
        if (!"pousada".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            reservaPousadaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = reservaPousadaConfirmHandler.stripReservaPousadaTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil academia (camada 7.7): se o tenant é academia e a resposta da IA
     * contém a tag {@code <matricula>}, cria a matrícula (MatriculaConfirmHandler resolve o contato)
     * e devolve um AiResponse SEM a tag. Para outro perfil, ou sem tag, devolve o original. Espelho
     * de {@link #maybeProcessPousadaReservation}. Best-effort.
     */
    private AiResponse maybeProcessMatricula(MessageInboundProcessedEvent event,
                                             UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !matriculaConfirmHandler.hasMatriculaTag(reply)) {
            return aiResponse;
        }
        if (!"academia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            matriculaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = matriculaConfirmHandler.stripMatriculaTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'pet' (camada 7.8) e a resposta da IA contenha a tag
     * {@code <agendamento_pet>}, cria o agendamento (AgendamentoPetConfirmHandler resolve o tutor e,
     * no modo new_animal, cadastra o animal antes) e devolve um AiResponse com a tag removida; senão
     * devolve o aiResponse original. Espelho de {@link #maybeProcessMatricula}. Tag distinta de
     * {@code <agendamento>} (salon). Best-effort.
     */
    private AiResponse maybeProcessPetAppointment(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !agendamentoPetConfirmHandler.hasAgendamentoTag(reply)) {
            return aiResponse;
        }
        if (!"pet".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            agendamentoPetConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = agendamentoPetConfirmHandler.stripAgendamentoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'oficina' (camada 7.9) e a resposta da IA contenha a tag
     * {@code <ordem_servico>}, ABRE a OS (AberturaOsConfirmHandler resolve o cliente e, no modo
     * new_vehicle, cadastra o veículo antes) e devolve um AiResponse com a tag removida; senão
     * devolve o aiResponse original. Tag distinta de todas as outras. Best-effort.
     */
    private AiResponse maybeProcessAberturaOs(MessageInboundProcessedEvent event,
                                              UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aberturaOsConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"oficina".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            aberturaOsConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = aberturaOsConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'oficina' (camada 7.9) e a resposta da IA contenha a tag
     * {@code <aprovacao_os>}, MUTA o estado da OS orçada (aprovada/recusada) e remove a tag. A
     * NOVIDADE da SM: a IA altera o estado de um artefato existente. Só atua sobre OS em 'orcada'
     * (o handler valida). Best-effort.
     */
    private AiResponse maybeProcessAprovacaoOs(MessageInboundProcessedEvent event,
                                               UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aprovacaoOsHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"oficina".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        aprovacaoOsHandler.parseAndApply(event.companyId(), conversationId, reply);
        String stripped = aprovacaoOsHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'nutri' (camada 8.0) e a resposta da IA contenha a tag
     * {@code <consulta_nutri>}, AGENDA a consulta (AgendamentoNutriConfirmHandler resolve o paciente
     * e, no modo new_patient, cadastra antes) e devolve um AiResponse com a tag removida; senão
     * devolve o aiResponse original. Tag distinta de {@code <consulta>} (dental). Best-effort.
     */
    private AiResponse maybeProcessConsultaNutri(MessageInboundProcessedEvent event,
                                                 UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !agendamentoNutriConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"nutri".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            agendamentoNutriConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = agendamentoNutriConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'nutri' (camada 8.0) e a resposta da IA contenha a tag
     * {@code <entrega_plano>}, ENTREGA o plano alimentar ATIVO do paciente — o EntregaPlanoHandler
     * envia o BODY EXATO gravado pelo profissional como uma mensagem outbound separada (NÃO passa
     * pela geração da IA, pra não ser reescrito) e só para paciente do PRÓPRIO contato (segurança).
     * A tag é removida da mensagem da IA (que pode ter um texto-wrapper "aqui está seu plano:").
     * Best-effort: sem plano ativo ou sem permissão → nada é entregue, a IA já orienta agendar.
     */
    private AiResponse maybeProcessEntregaPlano(MessageInboundProcessedEvent event,
                                                UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !entregaPlanoHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"nutri".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            entregaPlanoHandler.parseAndDeliver(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = entregaPlanoHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'barbearia' (camada 8.1) e a resposta da IA contenha a tag
     * {@code <agendamento_barbearia>}, MARCA o horário (AgendamentoBarbeariaConfirmHandler resolve o
     * contato, conflito por barbeiro) e devolve um AiResponse com a tag removida; senão devolve o
     * original. Tag distinta do {@code <agendamento>} do salon. Best-effort.
     */
    private AiResponse maybeProcessAgendamentoBarbearia(MessageInboundProcessedEvent event,
                                                        UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !agendamentoBarbeariaConfirmHandler.hasAgendamentoTag(reply)) {
            return aiResponse;
        }
        if (!"barbearia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            agendamentoBarbeariaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = agendamentoBarbeariaConfirmHandler.stripAgendamentoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'barbearia' (camada 8.1) e a resposta da IA contenha a tag
     * {@code <fila_barbearia>}, ENFILEIRA o cliente no walk-in (EntrarFilaHandler resolve o contato,
     * calcula posição/ETA derivados) e devolve um AiResponse com a tag removida; senão devolve o
     * original. Se a fila está desligada (queue_enabled=false), é no-op (nenhum ticket criado). A IA
     * informa a posição/espera estimadas na própria mensagem. Best-effort.
     */
    private AiResponse maybeProcessFilaBarbearia(MessageInboundProcessedEvent event,
                                                 UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !entrarFilaHandler.hasFilaTag(reply)) {
            return aiResponse;
        }
        if (!"barbearia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            entrarFilaHandler.parseAndEnqueue(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = entrarFilaHandler.stripFilaTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'barbearia' (onda 1 do backlog #1) e a resposta da IA contenha a tag
     * {@code <confirmacao_barbearia>}, aplica a DECISÃO DO CLIENTE ao agendamento (confirmado/
     * cancelado — ConfirmacaoBarbeariaHandler valida barreira de contato + máquina de status; fecha o
     * loop do lembrete "confirma? SIM/CANCELAR" do BarberReminderJob) e devolve um AiResponse com a
     * tag removida; senão devolve o original. Best-effort.
     */
    private AiResponse maybeProcessConfirmacaoBarbearia(MessageInboundProcessedEvent event,
                                                        UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !confirmacaoBarbeariaHandler.hasConfirmacaoTag(reply)) {
            return aiResponse;
        }
        if (!"barbearia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            confirmacaoBarbeariaHandler.parseAndApply(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = confirmacaoBarbeariaHandler.stripConfirmacaoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }


    /**
     * Caso o tenant seja perfil 'restaurant' (onda 1) e a resposta da IA contenha a tag
     * {@code <confirmacao_reserva>}, aplica a DECISÃO DO CLIENTE à reserva (confirmada/cancelada —
     * ConfirmacaoReservaHandler valida barreira de contato + máquina de status; fecha o loop do
     * lembrete "confirma? SIM/NÃO" do RestaurantReminderJob) e devolve um AiResponse com a tag
     * removida; senão devolve o original. Best-effort.
     */
    private AiResponse maybeProcessConfirmacaoReserva(MessageInboundProcessedEvent event,
                                                      UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !confirmacaoReservaHandler.hasConfirmacaoTag(reply)) {
            return aiResponse;
        }
        if (!"restaurant".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            confirmacaoReservaHandler.parseAndApply(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = confirmacaoReservaHandler.stripConfirmacaoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }


    /**
     * Caso o tenant seja perfil 'pousada' (onda 1) e a resposta da IA contenha a tag
     * {@code <confirmacao_pousada>}, aplica a DECISÃO DO HÓSPEDE à reserva (confirmado/cancelado —
     * ConfirmacaoPousadaHandler valida barreira de contato + máquina de status; fecha o loop do
     * lembrete de check-in D-1 do PousadaReminderJob) e devolve um AiResponse com a tag removida;
     * senão devolve o original. Best-effort.
     */
    private AiResponse maybeProcessConfirmacaoPousada(MessageInboundProcessedEvent event,
                                                      UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !confirmacaoPousadaHandler.hasConfirmacaoTag(reply)) {
            return aiResponse;
        }
        if (!"pousada".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            confirmacaoPousadaHandler.parseAndApply(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = confirmacaoPousadaHandler.stripConfirmacaoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }


    /**
     * Caso o tenant seja perfil 'pet' (onda 1) e a resposta da IA contenha a tag
     * {@code <confirmacao_pet>}, aplica a DECISÃO DO TUTOR ao agendamento (confirmado/cancelado —
     * ConfirmacaoPetHandler valida barreira de contato + máquina de status; fecha o loop do
     * lembrete de véspera do PetReminderJob) e devolve um AiResponse com a tag removida; senão
     * devolve o original. Best-effort.
     */
    private AiResponse maybeProcessConfirmacaoPet(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !confirmacaoPetHandler.hasConfirmacaoTag(reply)) {
            return aiResponse;
        }
        if (!"pet".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            confirmacaoPetHandler.parseAndApply(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = confirmacaoPetHandler.stripConfirmacaoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'eventos' (camada 8.2) e a resposta da IA contenha a tag
     * {@code <proposta_evento>}, ABRE a proposta (PropostaEventoConfirmHandler resolve o cliente da
     * conversa — snapshots; UM modo só, sem sub-entidade) e devolve um AiResponse com a tag removida;
     * senão devolve o aiResponse original. Tag distinta de todas as outras. Best-effort.
     */
    private AiResponse maybeProcessPropostaEvento(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !propostaEventoConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"eventos".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            propostaEventoConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = propostaEventoConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'eventos' (camada 8.2) e a resposta da IA contenha a tag
     * {@code <aprovacao_proposta>}, MUTA o estado da proposta orçada (aprovada/recusada) e remove a
     * tag — o gate de aprovação em 2 fases (clone do Oficina): a IA altera o estado de um artefato
     * existente. Só atua sobre proposta em 'orcada' (o handler valida). Best-effort.
     */
    private AiResponse maybeProcessAprovacaoProposta(MessageInboundProcessedEvent event,
                                                     UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aprovacaoPropostaHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"eventos".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        aprovacaoPropostaHandler.parseAndApply(event.companyId(), conversationId, reply);
        String stripped = aprovacaoPropostaHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'estetica' (camada 8.3) e a resposta da IA contenha a tag
     * {@code <agendamento_estetica>}, AGENDA a sessão (AgendamentoEsteticaConfirmHandler resolve o
     * contato; se houver package_id, CONSOME 1 sessão do pacote transacionalmente) e devolve um
     * AiResponse com a tag removida; senão devolve o original. Best-effort.
     */
    private AiResponse maybeProcessAgendamentoEstetica(MessageInboundProcessedEvent event,
                                                       UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !agendamentoEsteticaConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"estetica".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            agendamentoEsteticaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = agendamentoEsteticaConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Caso o tenant seja perfil 'estetica' (camada 8.3) e a resposta da IA contenha a tag
     * {@code <compra_pacote>}, registra a INTENÇÃO de compra de um pacote (CompraPacoteConfirmHandler
     * cria o pacote em 'pendente' com o PREÇO DO CATÁLOGO — a IA não inventa valor) e devolve um
     * AiResponse com a tag removida; senão devolve o original. A clínica confirma o pagamento depois.
     * Best-effort.
     */
    private AiResponse maybeProcessCompraPacote(MessageInboundProcessedEvent event,
                                                UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !compraPacoteConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"estetica".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            compraPacoteConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = compraPacoteConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil comida (camada 8.4): se o tenant é comida e a resposta da IA
     * contém a tag {@code <pedido_comida>}, cria o pedido de delivery (PedidoComidaConfirmHandler
     * resolve o contato, recalcula o total descartando o da IA, e snapshota as opções/adicionais
     * escolhidos) e devolve um AiResponse com o texto SEM a tag. Para outro perfil, ou sem tag,
     * devolve o aiResponse original. Espelho de {@link #maybeProcessSushiOrder}.
     *
     * <p>O pedido nasce em 'aguardando' — o aceite/recusa é AÇÃO HUMANA no painel (ESCAPADA 1), NÃO
     * existe handler de aceite da IA. Best-effort: falha em criar o pedido (item/opção inválida) NÃO
     * impede o envio (o handler loga e retorna empty). A tag é removida sempre que existir — o cliente
     * não pode ver JSON cru.
     */
    private AiResponse maybeProcessPedidoComida(MessageInboundProcessedEvent event,
                                                UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoComidaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"comida".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoComidaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoComidaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil floricultura (camada 8.5): se o tenant é floricultura e a resposta
     * da IA contém a tag {@code <pedido_flor>}, cria o pedido (PedidoFlorConfirmHandler valida a data
     * de entrega agendada, o destinatário e o cartão, recalcula o total descartando o da IA, e
     * snapshota as opções) e devolve um AiResponse SEM a tag. Espelho de maybeProcessPedidoComida.
     * Best-effort; tag sempre removida; só age se profile_id='floricultura'.
     */
    private AiResponse maybeProcessPedidoFlor(MessageInboundProcessedEvent event,
                                              UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoFlorConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"floricultura".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoFlorConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoFlorConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil pizzaria (camada 8.6): se o tenant é pizzaria e a resposta da IA
     * contém a tag {@code <pedido_pizza>}, cria o pedido (PedidoPizzaConfirmHandler resolve o contato,
     * recalcula o total descartando o da IA — incluindo a ESCAPADA meio-a-meio: preço da pizza pela
     * regra do MAIOR VALOR dos sabores das frações + Σ deltas — e snapshota opções e sabores) e
     * devolve um AiResponse SEM a tag. Espelho de maybeProcessPedidoComida. Best-effort; tag sempre
     * removida; só age se profile_id='pizzaria'.
     */
    private AiResponse maybeProcessPedidoPizza(MessageInboundProcessedEvent event,
                                               UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoPizzaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"pizzaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoPizzaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoPizzaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil adega (camada 8.9): se o tenant é adega e a resposta da IA contém a
     * tag {@code <pedido_adega>}, cria o pedido de bebidas (PedidoAdegaConfirmHandler resolve o contato,
     * recalcula o total descartando o da IA, e snapshota opções/modifiers) e devolve um AiResponse SEM a
     * tag. Espelho de maybeProcessPedidoComida + a ESCAPADA +18: a tag sem {@code age_confirmed=true} é
     * abortada SEM criar pedido (trava de faixa etária). Best-effort; tag sempre removida; só age se
     * profile_id='adega'.
     */
    private AiResponse maybeProcessPedidoAdega(MessageInboundProcessedEvent event,
                                               UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoAdegaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"adega".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoAdegaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoAdegaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil escola (camada 8.19): se o tenant é escola e a resposta da IA contém
     * a tag {@code <matricula_escola>}, registra a matrícula (MatriculaEscolaConfirmHandler resolve o
     * responsável da conversa, valida turma/aluno/capacity/anti-dupla; 2 modos: student_id existente OU
     * new_student cadastra o aluno sub-entidade E matricula) e devolve um AiResponse SEM a tag.
     * Best-effort; tag sempre removida; só age se profile_id='escola'.
     */
    private AiResponse maybeProcessMatriculaEscola(MessageInboundProcessedEvent event,
                                                   UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !matriculaEscolaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"escola".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            matriculaEscolaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = matriculaEscolaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil escola (camada 8.19): se o tenant é escola e a resposta da IA contém
     * a tag {@code <visita_escola>}, agenda a visita (VisitaEscolaConfirmHandler resolve o responsável,
     * valida data futura + período; agenda leve dia+período, SEM conflito) e devolve um AiResponse SEM a
     * tag. Best-effort; tag sempre removida; só age se profile_id='escola'.
     */
    private AiResponse maybeProcessVisitaEscola(MessageInboundProcessedEvent event,
                                                UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !visitaEscolaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"escola".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            visitaEscolaConfirmHandler.parseAndCreate(
                event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = visitaEscolaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil atelie (camada 8.14): se o tenant é atelie e a resposta da IA contém
     * a tag {@code <proposta_atelie>}, ABRE uma proposta em rascunho (PropostaAtelieConfirmHandler
     * resolve o contato, valida project_type, snapshota o cliente) e devolve um AiResponse SEM a tag.
     * Best-effort; tag sempre removida; só age se profile_id='atelie'.
     */
    private AiResponse maybeProcessPropostaAtelie(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !propostaAtelieConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"atelie".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            propostaAtelieConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = propostaAtelieConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil atelie (camada 8.14): se o tenant é atelie e a resposta da IA contém
     * a tag {@code <aprovacao_atelie>}, MUTA o estado da proposta orçada (aprovada/recusada) e remove a
     * tag — o gate de aprovação em 2 fases (clone do eventos). Só atua sobre proposta em 'orcada' (o
     * handler valida). Best-effort.
     */
    private AiResponse maybeProcessAprovacaoAtelie(MessageInboundProcessedEvent event,
                                                   UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aprovacaoAtelieHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"atelie".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        aprovacaoAtelieHandler.parseAndApply(event.companyId(), conversationId, contactId.orElse(null), reply);
        String stripped = aprovacaoAtelieHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil casamento (camada 8.7): se o tenant é casamento e a resposta da IA
     * contém a tag {@code <proposta_casamento>}, ABRE uma proposta em rascunho (PropostaCasamentoConfirmHandler
     * resolve o contato/noivos, snapshota o cliente) e devolve um AiResponse SEM a tag. Best-effort; tag
     * sempre removida; só age se profile_id='casamento'.
     */
    private AiResponse maybeProcessPropostaCasamento(MessageInboundProcessedEvent event,
                                                     UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !propostaCasamentoConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"casamento".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            propostaCasamentoConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = propostaCasamentoConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil casamento (camada 8.7): se o tenant é casamento e a resposta da IA
     * contém a tag {@code <aprovacao_casamento>}, MUTA o estado da proposta orçada (aprovada/recusada) e
     * remove a tag — o gate de aprovação em 2 fases (clone do eventos). Só atua sobre proposta em
     * 'orcada' (o handler valida). Best-effort.
     */
    private AiResponse maybeProcessAprovacaoCasamento(MessageInboundProcessedEvent event,
                                                      UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aprovacaoCasamentoHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"casamento".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        aprovacaoCasamentoHandler.parseAndApply(event.companyId(), conversationId, reply);
        String stripped = aprovacaoCasamentoHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil concessionaria (camada 8.17): se o tenant é concessionaria e a
     * resposta da IA contém a tag {@code <testdrive_carro>}, agenda o test-drive (TestDriveConfirmHandler
     * resolve o contato, valida veículo disponível, conflito por vendedor, materializa end_at) e devolve
     * um AiResponse SEM a tag. Best-effort; só age se profile_id='concessionaria'.
     */
    /**
     * Onda 1 do backlog concessionária (#1): se a resposta da IA contém a tag {@code <desejo_carro>},
     * registra a lista de desejos do contato (DesejoCarroConfirmHandler) e devolve a resposta SEM a
     * tag. O alerta dispara depois, quando um veículo disponível casar. Best-effort.
     */
    private AiResponse maybeProcessDesejoCarro(MessageInboundProcessedEvent event,
                                               UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !desejoCarroConfirmHandler.hasDesejoTag(reply)) {
            return aiResponse;
        }
        if (!"concessionaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            desejoCarroConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = desejoCarroConfirmHandler.stripDesejoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Onda 1 do backlog concessionária (#3): se a resposta da IA contém a tag
     * {@code <confirmacao_testdrive>}, aplica a DECISÃO DO CLIENTE ao test-drive (confirmado/cancelado
     * — ConfirmacaoTestDriveHandler valida barreira de contato + máquina de status; fecha o loop do
     * lembrete "confirma? SIM/CANCELAR") e devolve a resposta SEM a tag. Best-effort.
     */
    private AiResponse maybeProcessConfirmacaoTestDrive(MessageInboundProcessedEvent event,
                                                        UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !confirmacaoTestDriveHandler.hasConfirmacaoTag(reply)) {
            return aiResponse;
        }
        if (!"concessionaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            confirmacaoTestDriveHandler.parseAndApply(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = confirmacaoTestDriveHandler.stripConfirmacaoTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    private AiResponse maybeProcessTestDrive(MessageInboundProcessedEvent event,
                                             UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !testDriveConfirmHandler.hasTestdriveTag(reply)) {
            return aiResponse;
        }
        if (!"concessionaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            testDriveConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = testDriveConfirmHandler.stripTestdriveTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil concessionaria (camada 8.17): se o tenant é concessionaria e a
     * resposta da IA contém a tag {@code <lead_carro>}, registra o lead de compra (LeadCarroConfirmHandler
     * resolve o contato, valida veículo disponível, snapshota o preço do catálogo — NUNCA da tag) e
     * devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='concessionaria'.
     */
    private AiResponse maybeProcessLeadCarro(MessageInboundProcessedEvent event,
                                             UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !leadCarroConfirmHandler.hasLeadTag(reply)) {
            return aiResponse;
        }
        if (!"concessionaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            leadCarroConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = leadCarroConfirmHandler.stripLeadTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil lavanderia (camada 8.10): se o tenant é lavanderia e a resposta da IA
     * contém a tag {@code <pedido_lavanderia>}, cria o pedido de lavagem (PedidoLavanderiaConfirmHandler
     * resolve o contato, valida as 2 datas — coleta + entrega = coleta + MAX(turnaround) — e recalcula o
     * total descartando o da IA) e devolve um AiResponse SEM a tag. Best-effort; só age se
     * profile_id='lavanderia'.
     */
    private AiResponse maybeProcessPedidoLavanderia(MessageInboundProcessedEvent event,
                                                    UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoLavanderiaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"lavanderia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoLavanderiaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoLavanderiaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil dermatologia (camada 8.11): se o tenant é dermatologia e a resposta da
     * IA contém a tag {@code <consulta_derma>}, agenda a consulta (AgendamentoDermaConfirmHandler resolve
     * o contato, valida conflito por profissional, materializa end_at; 2 modos paciente) e devolve um
     * AiResponse SEM a tag. Best-effort; só age se profile_id='dermatologia'.
     */
    private AiResponse maybeProcessConsultaDerma(MessageInboundProcessedEvent event,
                                                 UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !agendamentoDermaConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"dermatologia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            agendamentoDermaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = agendamentoDermaConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil dermatologia (camada 8.11): se o tenant é dermatologia e a resposta da
     * IA contém a tag {@code <entrega_preparo>}, entrega READ-ONLY a nota de preparo do tipo da consulta
     * (EntregaPreparoHandler envia o texto VERBATIM via notifier, com barreira de contato) e devolve um
     * AiResponse SEM a tag. Best-effort; só age se profile_id='dermatologia'.
     */
    private AiResponse maybeProcessEntregaPreparo(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !entregaPreparoHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"dermatologia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            entregaPreparoHandler.parseAndDeliver(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = entregaPreparoHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil fotografia (camada 8.16): se o tenant é fotografia e a resposta da
     * IA contém a tag {@code <sessao_foto>}, agenda a sessão (SessaoFotoConfirmHandler resolve o
     * contato, valida conflito por profissional, snapshota o pacote, materializa end_at e
     * delivery_due_date) e devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='fotografia'.
     */
    private AiResponse maybeProcessSessaoFoto(MessageInboundProcessedEvent event,
                                              UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !sessaoFotoConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"fotografia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            sessaoFotoConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = sessaoFotoConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil fotografia (camada 8.16): se o tenant é fotografia e a resposta da
     * IA contém a tag {@code <entrega_material>}, entrega READ-ONLY o link do material gravado na
     * sessão (EntregaMaterialHandler envia o link VERBATIM via notifier, com barreira de contato) e
     * devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='fotografia'.
     */
    private AiResponse maybeProcessEntregaMaterial(MessageInboundProcessedEvent event,
                                                   UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !entregaMaterialHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"fotografia".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            entregaMaterialHandler.parseAndDeliver(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = entregaMaterialHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil cursos (camada 8.20): se o tenant é cursos e a resposta da IA contém
     * a tag {@code <matricula_curso>}, matricula o aluno no curso (assinatura; anti-dupla por curso) e
     * devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='cursos'.
     */
    private AiResponse maybeProcessMatriculaCurso(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !matriculaCursoConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"cursos".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            matriculaCursoConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = matriculaCursoConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil cursos (camada 8.20): se o tenant é cursos e a resposta da IA contém
     * a tag {@code <entrega_modulo>}, entrega READ-ONLY o conteúdo do próximo módulo da matrícula
     * (EntregaModuloHandler envia VERBATIM via notifier, com barreira de contato, e grava o progresso)
     * e devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='cursos'.
     */
    private AiResponse maybeProcessEntregaModulo(MessageInboundProcessedEvent event,
                                                 UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !entregaModuloHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"cursos".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            entregaModuloHandler.parseAndDeliver(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = entregaModuloHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil lingerie (camada 8.21): se o tenant é lingerie e a resposta da IA
     * contém a tag {@code <pedido_lingerie>}, cria o pedido de varejo (PedidoLingerieConfirmHandler
     * resolve o contato, resolve as variantes, DECREMENTA o estoque transacionalmente — out_of_stock
     * aborta o pedido —, recalcula o total descartando o da IA, e snapshota produto/variante) e devolve
     * um AiResponse SEM a tag. Best-effort; tag sempre removida; só age se profile_id='lingerie'.
     */
    private AiResponse maybeProcessPedidoLingerie(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoLingerieConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"lingerie".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoLingerieConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoLingerieConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil moda_infantil (camada 8.22): se o tenant é moda_infantil e a resposta
     * da IA contém a tag {@code <pedido_moda_infantil>}, cria o pedido de varejo infantil
     * (PedidoModaInfantilConfirmHandler resolve o contato, resolve as variantes faixa-etária×cor,
     * DECREMENTA o estoque transacionalmente — out_of_stock aborta —, recalcula o total descartando o da
     * IA, e snapshota produto/variante) e devolve um AiResponse SEM a tag. Best-effort; tag sempre
     * removida; só age se profile_id='moda_infantil'.
     */
    private AiResponse maybeProcessPedidoModaInfantil(MessageInboundProcessedEvent event,
                                                      UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoModaInfantilConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"moda_infantil".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoModaInfantilConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoModaInfantilConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil las (camada 8.23): se o tenant é las e a resposta da IA contém a tag
     * {@code <pedido_las>}, cria o pedido de varejo de lãs (PedidoLasConfirmHandler resolve o contato,
     * resolve as variantes cor×dye_lot, DECREMENTA o estoque transacionalmente — out_of_stock aborta —,
     * valida a regra same_lot_guaranteed — mixed_dye_lots aborta —, recalcula o total descartando o da IA,
     * e snapshota produto/cor/lote) e devolve um AiResponse SEM a tag. Best-effort; tag sempre removida;
     * só age se profile_id='las'.
     */
    private AiResponse maybeProcessPedidoLas(MessageInboundProcessedEvent event,
                                             UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoLasConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"las".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoLasConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoLasConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil padaria (camada 8.8): se o tenant é padaria e a resposta da IA contém
     * a tag {@code <encomenda_padaria>}, cria o pedido de padaria/confeitaria (EncomendaPadariaConfirmHandler
     * resolve o contato, recalcula o total descartando o da IA, snapshota opções/personalização,
     * valida lead time dos itens sob encomenda e a regra de fulfillment) e devolve um AiResponse SEM a
     * tag. Best-effort; tag sempre removida; só age se profile_id='padaria'.
     */
    private AiResponse maybeProcessEncomendaPadaria(MessageInboundProcessedEvent event,
                                                    UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !encomendaPadariaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"padaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            encomendaPadariaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = encomendaPadariaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil otica (camada 8.12, FLUXO A): se o tenant é otica e a resposta da IA
     * contém a tag {@code <exame_otica>}, agenda o exame de vista (ExameOticaConfirmHandler resolve o
     * contato, valida conflito por optometrista, materializa end_at) e devolve um AiResponse SEM a tag.
     * Best-effort; só age se profile_id='otica'.
     */
    private AiResponse maybeProcessExameOtica(MessageInboundProcessedEvent event,
                                              UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !exameOticaConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"otica".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            exameOticaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = exameOticaConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil otica (camada 8.12, FLUXO B): se o tenant é otica e a resposta da IA
     * contém a tag {@code <encomenda_otica>}, cria a encomenda de óculos (EncomendaOticaConfirmHandler
     * resolve o contato, recalcula o total, valida o lead time, registra os dados de receita como campos
     * administrativos SEM interpretar o grau) e devolve um AiResponse SEM a tag. Best-effort; só age se
     * profile_id='otica'.
     */
    private AiResponse maybeProcessEncomendaOtica(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !encomendaOticaConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"otica".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            encomendaOticaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = encomendaOticaConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil papelaria (camada 8.15): se o tenant é papelaria e a resposta da IA
     * contém a tag {@code <pedido_papelaria>}, cria a encomenda gráfica (PedidoPapelariaConfirmHandler
     * resolve o contato, recalcula o total — tiragem × unit —, snapshota personalização, valida lead time
     * e fulfillment) e devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='papelaria'.
     */
    private AiResponse maybeProcessPedidoPapelaria(MessageInboundProcessedEvent event,
                                                   UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoPapelariaConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"papelaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoPapelariaConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoPapelariaConfirmHandler.stripOrderTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil papelaria (camada 8.15): se o tenant é papelaria e a resposta da IA
     * contém a tag {@code <aprovacao_arte>}, CAPTURA a aprovação da arte declarada pelo cliente
     * (AprovacaoArteHandler seta art_approved=true e move arte_aprovacao→em_producao, só se o pedido está
     * em 'arte_aprovacao'; senão no-op) e devolve um AiResponse SEM a tag. Best-effort; só age se
     * profile_id='papelaria'. A IA NUNCA aprova a arte pelo cliente — só registra a aprovação declarada.
     */
    private AiResponse maybeProcessAprovacaoArte(MessageInboundProcessedEvent event,
                                                 UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aprovacaoArteHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"papelaria".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            aprovacaoArteHandler.parseAndApply(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = aprovacaoArteHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil viagens (camada 8.18): se o tenant é viagens e a resposta da IA contém
     * a tag {@code <proposta_viagem>}, ABRE a proposta de viagem em rascunho (PropostaViagemConfirmHandler
     * resolve o contato, snapshota cliente; consultant/datas inválidos são ignorados mas a proposta abre)
     * e devolve um AiResponse SEM a tag. Best-effort; só age se profile_id='viagens'.
     */
    private AiResponse maybeProcessPropostaViagem(MessageInboundProcessedEvent event,
                                                  UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !propostaViagemConfirmHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"viagens".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            propostaViagemConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = propostaViagemConfirmHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil viagens (camada 8.18): se o tenant é viagens e a resposta da IA contém
     * a tag {@code <aprovacao_viagem>}, CAPTURA a aprovação/recusa declarada pelo cliente
     * (AprovacaoViagemHandler aplica só se a proposta está 'orcada'; senão no-op) e devolve um AiResponse
     * SEM a tag. Best-effort; só age se profile_id='viagens'.
     */
    private AiResponse maybeProcessAprovacaoViagem(MessageInboundProcessedEvent event,
                                                   UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !aprovacaoViagemHandler.hasTag(reply)) {
            return aiResponse;
        }
        if (!"viagens".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        aprovacaoViagemHandler.parseAndApply(event.companyId(), conversationId, reply);
        String stripped = aprovacaoViagemHandler.stripTag(reply);
        return new AiResponse(stripped, aiResponse.needsHuman(), aiResponse.reason(),
            aiResponse.tokensIn(), aiResponse.tokensOut(), aiResponse.latencyMs(),
            aiResponse.schedulingIntent(), aiResponse.insights());
    }

    /**
     * Pós-processamento do perfil suplementos (camada 8.24): se o tenant é suplementos e a resposta da IA
     * contém a tag {@code <pedido_suplementos>}, cria o pedido de varejo (PedidoSuplementosConfirmHandler
     * resolve o contato, resolve as variantes sabor×peso, DECREMENTA o estoque transacionalmente —
     * out_of_stock aborta —, recalcula o total descartando o da IA, e snapshota produto/variante) e devolve
     * um AiResponse SEM a tag. Best-effort; só age se profile_id='suplementos'.
     */
    private AiResponse maybeProcessPedidoSuplementos(MessageInboundProcessedEvent event,
                                                     UUID conversationId, AiResponse aiResponse) {
        String reply = aiResponse.reply();
        if (reply == null || !pedidoSuplementosConfirmHandler.hasOrderTag(reply)) {
            return aiResponse;
        }
        if (!"suplementos".equals(companyProfileRepository.findProfileId(event.companyId()))) {
            return aiResponse;
        }
        Optional<UUID> contactId = conversationRepository.findContactIdByConversation(conversationId);
        if (contactId.isPresent()) {
            pedidoSuplementosConfirmHandler.parseAndCreate(event.companyId(), conversationId, contactId.get(), reply);
        }
        String stripped = pedidoSuplementosConfirmHandler.stripOrderTag(reply);
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
