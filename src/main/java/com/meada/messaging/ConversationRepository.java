package com.meada.messaging;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code conversations}. Resolve a conversa ABERTA de um contato numa
 * instância, ou cria uma nova — de forma idempotente sob concorrência.
 *
 * <p>Política (decisão do 06_unique_open_conversation): no máximo UMA conversa
 * 'open' por (contact, instance); 'closed' é estado final imutável; mensagem nova
 * de um contato cuja conversa anterior está fechada CRIA uma nova aberta.
 */
@Repository
public class ConversationRepository {

    private static final RowMapper<Conversation> ROW_MAPPER = (rs, rowNum) ->
        new Conversation(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"));

    // Caminho quente: conversa aberta já existe (mensagens seguintes do mesmo atendimento).
    private static final String SELECT_OPEN =
        "select id, company_id from conversations "
            + "where company_id = ? and contact_id = ? and whatsapp_instance_id = ? "
            + "and status = 'open'";

    // Cria a conversa aberta. ON CONFLICT repete o predicado parcial
    // (WHERE status='open') para o Postgres reconhecer
    // uq_conversations_open_per_contact_instance como arbiter.
    // DO NOTHING (não há nada a atualizar numa conversa existente no resolve):
    //   - INSERT novo → RETURNING traz a linha;
    //   - conflito (já existe uma open) → RETURNING VAZIO → reselect.
    // status='open' e handled_by='ai' explícitos (defaults do schema, fixados aqui
    //   para deixar a intenção clara). last_message_at e assigned_user_id ficam NULL
    //   na criação: last_message_at é atualizado por touchLastMessageAt (abaixo)
    //   quando uma mensagem é inserida; conversa nasce sem agente atribuído.
    private static final String INSERT_OPEN =
        "insert into conversations (company_id, contact_id, whatsapp_instance_id, status, handled_by) "
            + "values (?, ?, ?, 'open', 'ai') "
            + "on conflict (contact_id, whatsapp_instance_id) where status = 'open' "
            + "do nothing "
            + "returning id, company_id";

    // Variante WEB (camada 5.25 #73): idêntica ao INSERT_OPEN, mas marca channel='web' para
    // distinguir a origem da conversa. whatsapp_instance_id NÃO é nullable no schema (FK composta
    // NOT NULL), então o canal web REUTILIZA uma instância existente da empresa como portadora da
    // FK — o channel é o que distingue (decisão documentada no WebChatService). O arbiter do
    // ON CONFLICT é o mesmo índice parcial (contact_id, whatsapp_instance_id) where status='open':
    // contatos web têm phone sintético próprio, então não colidem com conversas WhatsApp.
    private static final String INSERT_OPEN_WEB =
        "insert into conversations (company_id, contact_id, whatsapp_instance_id, status, handled_by, channel) "
            + "values (?, ?, ?, 'open', 'ai', 'web') "
            + "on conflict (contact_id, whatsapp_instance_id) where status = 'open' "
            + "do nothing "
            + "returning id, company_id";

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve a conversa aberta de {@code (companyId, contactId, whatsappInstanceId)},
     * criando uma nova se não houver aberta. Idempotente: chamadas concorrentes
     * convergem para a mesma conversa aberta.
     *
     * <p>select-then-upsert-then-reselect:
     * <ol>
     *   <li>SELECT a aberta — se achou, retorna (caminho quente: atendimento em curso).
     *   <li>INSERT ON CONFLICT DO NOTHING — se RETURNING traz linha, retorna (criada).
     *   <li>Reselect — RETURNING vazio = outra thread criou a aberta entre 1 e 2; reselect.
     * </ol>
     *
     * <p>Diferente do ContactRepository, aqui o RETURNING vazio só acontece na race
     * (DO NOTHING, não DO UPDATE) — não há "preenchimento" que curto-circuite o passo 1.
     */
    public Conversation resolveOpenOrCreate(UUID companyId, UUID contactId, UUID whatsappInstanceId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(whatsappInstanceId, "whatsappInstanceId must not be null");

        // 1. caminho quente
        Optional<Conversation> open = selectOpen(companyId, contactId, whatsappInstanceId);
        if (open.isPresent()) {
            return open.get();
        }

        // 2. cria
        List<Conversation> created = jdbcTemplate.query(
            INSERT_OPEN, ROW_MAPPER, companyId, contactId, whatsappInstanceId);
        if (!created.isEmpty()) {
            return created.get(0);
        }

        // 3. reselect (race: outra thread criou a aberta entre 1 e 2)
        return selectOpen(companyId, contactId, whatsappInstanceId)
            .orElseThrow(() -> new IllegalStateException(
                "Open conversation disappeared after ON CONFLICT for company=" + companyId
                    + " contact=" + contactId + " instance=" + whatsappInstanceId));
    }

    /**
     * Resolve a conversa aberta do canal WEB de {@code (companyId, contactId, whatsappInstanceId)},
     * criando uma nova (channel='web') se não houver aberta. Mesmo contrato idempotente do
     * {@link #resolveOpenOrCreate} — só muda o channel da linha criada para 'web'.
     *
     * <p>{@code whatsappInstanceId} é a instância da empresa REUTILIZADA como portadora da FK
     * NOT NULL conversations.whatsapp_instance_id (não existe instância "web" no schema desta
     * fase — ver javadoc do WebChatService). O caller (WebChatService) resolve essa instância
     * via WebChatRepository.findAnyInstanceId.
     */
    public Conversation resolveOpenWebOrCreate(UUID companyId, UUID contactId, UUID whatsappInstanceId) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(contactId, "contactId must not be null");
        Objects.requireNonNull(whatsappInstanceId, "whatsappInstanceId must not be null");

        // 1. caminho quente: já há uma conversa aberta deste contato web.
        Optional<Conversation> open = selectOpen(companyId, contactId, whatsappInstanceId);
        if (open.isPresent()) {
            return open.get();
        }

        // 2. cria (channel='web')
        List<Conversation> created = jdbcTemplate.query(
            INSERT_OPEN_WEB, ROW_MAPPER, companyId, contactId, whatsappInstanceId);
        if (!created.isEmpty()) {
            return created.get(0);
        }

        // 3. reselect (race: outra thread criou a aberta entre 1 e 2)
        return selectOpen(companyId, contactId, whatsappInstanceId)
            .orElseThrow(() -> new IllegalStateException(
                "Open web conversation disappeared after ON CONFLICT for company=" + companyId
                    + " contact=" + contactId + " instance=" + whatsappInstanceId));
    }

    private Optional<Conversation> selectOpen(UUID companyId, UUID contactId, UUID whatsappInstanceId) {
        return jdbcTemplate.query(SELECT_OPEN, ROW_MAPPER, companyId, contactId, whatsappInstanceId)
            .stream()
            .findFirst();
    }

    /**
     * Atualiza {@code last_message_at} da conversa — chamado pelo WebhookService
     * após inserir uma mensagem, na mesma transação. Mora aqui (não no
     * MessageRepository) porque é UPDATE em conversations: cada repositório toca
     * só a sua tabela.
     *
     * @param conversationId conversa a tocar
     * @param when           timestamp da última mensagem (o serviço passa explícito)
     */
    public void touchLastMessageAt(UUID conversationId, Instant when) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(when, "when must not be null");
        jdbcTemplate.update(
            "update conversations set last_message_at = ?, updated_at = now() where id = ?",
            Timestamp.from(when), conversationId);
    }

    /**
     * Transfere a conversa para atendimento humano (handled_by 'ai' → 'human').
     * Chamado pelo OutboundService quando a IA pede humano, ou quando a IA/envio
     * falha após retries (ver matriz de fluxo da Fase 3.3).
     *
     * <p>O {@code AND handled_by = 'ai'} torna a operação IDEMPOTENTE: um flip
     * redundante (conversa já 'human') atualiza 0 linhas e retorna false. Isso não
     * é erro — é informação de log (o caller distingue "flipei agora" de "já estava
     * humana"). updated_at = now() por coerência com touchLastMessageAt (toda
     * modificação da conversa atualiza o timestamp).
     *
     * @return true se transferiu (era 'ai'); false se a conversa não existe ou já
     *         estava 'human'.
     */
    public boolean markHandledByHuman(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        int updated = jdbcTemplate.update(
            "update conversations set handled_by = 'human', updated_at = now() "
                + "where id = ? and handled_by = 'ai'",
            conversationId);
        return updated > 0;
    }

    /**
     * Lê o {@code handled_by} atual da conversa — usado pelo OutboundService como
     * pré-condição (só processa IA se a conversa ainda está 'ai'; se um humano
     * assumiu, pula). Leitura fresca, pós-commit do inbound.
     *
     * @return "ai" ou "human", ou {@link Optional#empty()} se a conversa não existe
     *         (ex.: deletada entre o evento e o processamento async).
     */
    public Optional<String> findHandledBy(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(
                "select handled_by from conversations where id = ?",
                (rs, rowNum) -> rs.getString("handled_by"), conversationId)
            .stream()
            .findFirst();
    }

    /**
     * Grava a intenção de agendamento detectada pela IA em {@code scheduling_intent}
     * (camada 5.15 #29). Chamado pelo OutboundService quando o AiResponse traz intent —
     * SÓ nos casos com AiResponse válido (caminho feliz e handoff-com-reply). A maioria
     * das mensagens NÃO detecta intent, então o caller só invoca este método quando há
     * intent (não chama com null — evita UPDATE inútil em toda mensagem).
     *
     * <p>{@code ?::jsonb} faz o cast do parâmetro String (JSON serializado no Java) para
     * jsonb no Postgres. updated_at = now() por coerência com os outros UPDATEs daqui.
     * Auditado automaticamente via trg_conversations_audit (fase-5.3).
     *
     * @param conversationId conversa a marcar
     * @param intentJsonb    JSON da intent já serializado (não-null; o caller garante)
     */
    public void updateSchedulingIntent(UUID conversationId, String intentJsonb) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(intentJsonb, "intentJsonb must not be null");
        jdbcTemplate.update(
            "update conversations set scheduling_intent = ?::jsonb, updated_at = now() where id = ?",
            intentJsonb, conversationId);
    }

    /**
     * Grava a intenção de cancelamento detectada pela IA em {@code cancellation_intent}
     * (camada 5.18 #51). Mesmo contrato do {@link #updateSchedulingIntent}: chamado só quando
     * o AiResponse traz a detecção (a maioria das mensagens NÃO detecta — evita UPDATE inútil).
     *
     * @param conversationId conversa a marcar
     * @param intentJsonb    JSON da intent já serializado (não-null; o caller garante)
     */
    public void updateCancellationIntent(UUID conversationId, String intentJsonb) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(intentJsonb, "intentJsonb must not be null");
        jdbcTemplate.update(
            "update conversations set cancellation_intent = ?::jsonb, updated_at = now() where id = ?",
            intentJsonb, conversationId);
    }

    /**
     * Grava a intenção de reclamação detectada pela IA em {@code complaint_intent}
     * (camada 5.18 #52). Quando presente, o OutboundService FORÇA handoff (handled_by='human')
     * logo após este UPDATE. Mesmo contrato dos demais updaters de intent.
     *
     * @param conversationId conversa a marcar
     * @param intentJsonb    JSON da intent já serializado (não-null; o caller garante)
     */
    public void updateComplaintIntent(UUID conversationId, String intentJsonb) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(intentJsonb, "intentJsonb must not be null");
        jdbcTemplate.update(
            "update conversations set complaint_intent = ?::jsonb, updated_at = now() where id = ?",
            intentJsonb, conversationId);
    }

    /**
     * Grava os dados estruturados coletados pela IA em {@code extracted_data}
     * (camada 5.18 #53). Objeto livre (nome/email/cpf/etc.). Mesmo contrato dos demais
     * updaters de intent: chamado só quando o AiResponse traz a detecção.
     *
     * @param conversationId conversa a marcar
     * @param dataJsonb      JSON dos dados já serializado (não-null; o caller garante)
     */
    public void updateExtractedData(UUID conversationId, String dataJsonb) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(dataJsonb, "dataJsonb must not be null");
        jdbcTemplate.update(
            "update conversations set extracted_data = ?::jsonb, updated_at = now() where id = ?",
            dataJsonb, conversationId);
    }

    /**
     * Resolve o {@code contact_id} dono de uma conversa — usado pelo OutboundService para
     * persistir as detecções por-contato (contact_memory #55, detected_tone #58) que vivem
     * em contacts, não em conversations.
     *
     * @return o id do contato, ou {@link Optional#empty()} se a conversa não existe.
     */
    public Optional<UUID> findContactIdByConversation(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(
                "select contact_id from conversations where id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("contact_id"), conversationId)
            .stream()
            .findFirst();
    }

    /**
     * Resolve o {@code whatsapp_instance_id} de uma conversa — usado pelo ReminderJob (camada
     * 5.19 #63) para descobrir por qual instância mandar o lembrete (as credenciais da Evolution
     * são per-instância). Optional vazio se a conversa não existe.
     *
     * @return o id da instância, ou {@link Optional#empty()} se a conversa não existe.
     */
    public Optional<UUID> findInstanceIdByConversation(UUID conversationId) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        return jdbcTemplate.query(
                "select whatsapp_instance_id from conversations where id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("whatsapp_instance_id"), conversationId)
            .stream()
            .findFirst();
    }
}
