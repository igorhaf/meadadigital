package com.meada.whatsapp.messaging;

import com.meada.whatsapp.ai.ConversationTurn;
import com.meada.whatsapp.ai.ConversationTurn.Role;
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
        "insert into messages (company_id, conversation_id, direction, sender, content, evolution_message_id) "
            + "values (?, ?, ?, ?, ?, ?) "
            + "on conflict (evolution_message_id) where evolution_message_id is not null "
            + "do nothing "
            + "returning id, company_id";

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
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(content, "content must not be null");
        // evolutionMessageId pode ser null (mensagens internas) — sem requireNonNull.

        List<Message> inserted = jdbcTemplate.query(
            INSERT_IF_NEW, ROW_MAPPER,
            companyId, conversationId, direction.dbValue(), sender.dbValue(), content, evolutionMessageId);

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
}
