package com.meada.messaging;

import com.meada.ai.ConversationTurn;
import com.meada.ai.ConversationTurn.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso de escrita a {@code messages}. Insere mensagens de forma idempotente
 * (reentrega de webhook não duplica).
 *
 * <p>Toca SÓ a tabela messages — NÃO atualiza conversations.last_message_at (isso
 * é {@link ConversationRepository#touchLastMessageAt}, chamado pelo WebhookService
 * na mesma transação). Fronteira: cada repositório cuida da sua tabela.
 */
@Repository
public class MessageRepository {

    private static final RowMapper<Message> ROW_MAPPER = (rs, rowNum) ->
        new Message(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("company_id"));

    // Histórico para o prompt da IA: sender → Role (contact=USER; ai/human=ASSISTANT,
    // ambos são "nosso lado" do ponto de vista do modelo).
    private static final RowMapper<ConversationTurn> TURN_MAPPER = (rs, rowNum) ->
        new ConversationTurn(
            "contact".equals(rs.getString("sender")) ? Role.USER : Role.ASSISTANT,
            rs.getString("content"));

    // Busca as N mais recentes (DESC + limit), depois inverte para ordem
    // cronológica em memória (a IA lê o diálogo do início ao fim).
    //   order by created_at DESC, id DESC: o tiebreaker (id) é DESEMPATE de
    //   COLISÃO de timestamp (várias mensagens no mesmo instante em rajada ou em
    //   testes rápidos) — mesmo motivo do FAQ. NÃO é ordem semântica (uuid v4 não
    //   tem). created_at não é único; sem o tiebreaker a ordem entre colisões fica
    //   indefinida. (uq_messages_evolution_id é o único unique e não serve à ordem.)
    private static final String SELECT_RECENT =
        "select sender, content from messages "
            + "where conversation_id = ? "
            + "order by created_at desc, id desc "
            + "limit ?";

    // Insert idempotente. ON CONFLICT repete o predicado parcial
    // (WHERE evolution_message_id IS NOT NULL) para o Postgres reconhecer
    // uq_messages_evolution_id como arbiter.
    //   - evolution_message_id NÃO-NULL, novo  → insere, RETURNING traz a linha;
    //   - evolution_message_id NÃO-NULL, repetido (reentrega) → DO NOTHING,
    //     RETURNING VAZIO → caller trata como "já processada";
    //   - evolution_message_id NULL (mensagens internas ai/human antes do envio)
    //     → o índice parcial não cobre NULL, então NUNCA conflita: sempre insere.
    // Idempotência é GLOBAL (índice sem company_id): evolution_message_id é o id
    //   da mensagem no WhatsApp, único por natureza. Mesmo id em 2 tenants → só o
    //   1º insere. Correto e mais defensivo (reentrega cross-instance não duplica).
    private static final String INSERT_IF_NEW =
        "insert into messages (company_id, conversation_id, direction, sender, content, evolution_message_id, "
            + "tokens_in, tokens_out, model) "
            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) "
            + "on conflict (evolution_message_id) where evolution_message_id is not null "
            + "do nothing "
            + "returning id, company_id";

    // Conta as inbound de TODAS as conversas de um contato (camada 5.21 #82). JOIN
    // messages → conversations pelo conversation_id, filtrando contact_id + direction='inbound'.
    // Usado para detectar a 1ª mensagem do contato no histórico inteiro (não só na conversa atual):
    // como o webhook persiste a inbound ANTES de disparar o evento, "primeira mensagem" significa
    // count == 1 em produção.
    private static final String COUNT_INBOUND_BY_CONTACT =
        "select count(*) from messages m "
            + "join conversations cv on cv.id = m.conversation_id "
            + "where cv.contact_id = ? and m.direction = 'inbound'";

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insere a mensagem se ela ainda não existe (por evolution_message_id).
     *
     * @param evolutionMessageId id externo da Evolution; pode ser null para
     *                           mensagens internas (ai/human) sem id de envio ainda
     *                           — nesse caso não há idempotência (sempre insere).
     * @return a mensagem inserida, ou {@link Optional#empty()} se já existia
     *         (reentrega de webhook) — o caller pula o reprocessamento.
     */
    public Optional<Message> insertIfNew(UUID companyId,
                                         UUID conversationId,
                                         MessageDirection direction,
                                         MessageSender sender,
                                         String content,
                                         String evolutionMessageId) {
        // Sobrecarga histórica (6 args) — mensagens sem custo de IA a registrar (inbound,
        // mensagens internas). Delega à versão de tokens com tokens=null/model=null.
        return insertIfNew(companyId, conversationId, direction, sender, content,
            evolutionMessageId, null, null, null);
    }

    /**
     * Insere a mensagem se ela ainda não existe (por evolution_message_id), gravando também
     * os tokens consumidos e o modelo da IA (camada 6.2.5).
     *
     * <p><b>tokens_in/tokens_out/model NULLABLE de propósito:</b> NULL distingue "esta mensagem
     * não passou pela IA" (boas-vindas, fora-de-horário — métricas sintéticas, sem custo) de
     * "IA respondeu com custo zero" (0 tokens). O OutboundService passa null nesses casos
     * sintéticos; só o reply real da IA traz tokens > 0 + o nome do modelo. Métricas globais
     * (6.3) somam coalesce(tokens, 0), então NULL não infla o consumo.
     *
     * @param tokensIn  tokens do prompt (usageMetadata da IA); null se não houve IA.
     * @param tokensOut tokens da resposta; null se não houve IA.
     * @param model     nome do modelo (gemini.model) que gerou a resposta; null se não houve IA.
     */
    public Optional<Message> insertIfNew(UUID companyId,
                                         UUID conversationId,
                                         MessageDirection direction,
                                         MessageSender sender,
                                         String content,
                                         String evolutionMessageId,
                                         Integer tokensIn,
                                         Integer tokensOut,
                                         String model) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(content, "content must not be null");
        // evolutionMessageId pode ser null (mensagens internas) — sem requireNonNull.
        // tokensIn/tokensOut/model podem ser null (mensagens sem IA) — sem requireNonNull.

        List<Message> inserted = jdbcTemplate.query(
            INSERT_IF_NEW, ROW_MAPPER,
            companyId, conversationId, direction.dbValue(), sender.dbValue(), content,
            evolutionMessageId, tokensIn, tokensOut, model);

        return inserted.stream().findFirst();
    }

    /**
     * Últimas {@code limit} mensagens da conversa, em ordem CRONOLÓGICA (mais
     * antiga primeiro), como turns para o prompt da IA.
     *
     * <p>{@code limit} é teto, não piso: conversa com 5 mensagens e limit 20
     * retorna 5; com 50 e limit 20 retorna as 20 mais recentes. A query busca
     * DESC + limit (pega as recentes) e este método INVERTE para cronológica.
     *
     * @param limit máximo de turns (as mais recentes)
     * @return turns em ordem cronológica; lista vazia se a conversa não tem mensagens
     */
    public List<ConversationTurn> findRecentByConversation(UUID conversationId, int limit) {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        List<ConversationTurn> recentFirst =
            jdbcTemplate.query(SELECT_RECENT, TURN_MAPPER, conversationId, limit);
        // recentFirst está em ordem decrescente (mais recente → mais antiga);
        // inverte para cronológica (mais antiga → mais recente).
        List<ConversationTurn> chronological = new ArrayList<>(recentFirst);
        Collections.reverse(chronological);
        return chronological;
    }

    /**
     * Conta as mensagens inbound de um contato em TODO o histórico (todas as suas conversas).
     * Usado pelo OutboundService (camada 5.21 #82) para detectar a 1ª mensagem do contato.
     *
     * <p>Semântica de "primeira mensagem": o webhook persiste a inbound ANTES de publicar o
     * evento que o OutboundService processa — então, no momento do process(), a inbound atual
     * já está na tabela e {@code count == 1} significa "esta é a primeira de todas". O caller
     * trata {@code count <= 1} como primeira (defensivo contra o caso em que o teste/fluxo
     * direto não pré-persistiu a inbound).
     *
     * @param contactId contato dono das conversas a contar
     * @return número de mensagens inbound do contato em todo o histórico
     */
    public long countInboundForContact(UUID contactId) {
        Objects.requireNonNull(contactId, "contactId must not be null");
        Long count = jdbcTemplate.queryForObject(COUNT_INBOUND_BY_CONTACT, Long.class, contactId);
        return count == null ? 0L : count;
    }
}
