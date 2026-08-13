package com.meada.training;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Acesso a {@code ai_message_feedback} (camada 5.25 #57 — modo treinamento). O tenant marca
 * respostas da IA como boa/ruim e opcionalmente fornece uma correção, material para curadoria
 * manual do prompt.
 *
 * <p>Opera como service_role (o FeedbackController vive sob {@code /admin/}, autenticado pelo
 * JwtAuthenticationFilter). O isolamento por empresa NÃO depende do RLS aqui (service_role faz
 * BYPASSRLS): vem do {@code company_id} do authenticatedUser, sempre passado pelo controller —
 * nunca de input do cliente. Mesmo padrão dos demais repositórios admin (ex. SavedReplyRepository).
 */
@Repository
public class FeedbackRepository {

    // Upsert por (message_id) — UNIQUE no schema. Cada mensagem da IA tem no máximo um feedback;
    // reenviar muda o rating/correção (ON CONFLICT DO UPDATE). company_id e created_by vêm do
    // authenticatedUser (nunca do cliente). RETURNING informa se foi insert (xmax=0) ou update —
    // o controller usa para devolver 201 vs 200.
    //
    // ISOLAMENTO POR EMPRESA: o controller roda como service_role (BYPASSRLS), então a FK
    // message_id sozinha aceitaria mensagem de OUTRO tenant (a linha existe globalmente). Para
    // impedir feedback cross-tenant, o INSERT é guardado por um SELECT que só produz linha quando a
    // mensagem pertence à empresa do authenticatedUser. Mensagem inexistente/de outra empresa →
    // zero linhas no SELECT → nada inserido → RETURNING vazio → o repo sinaliza "não encontrada".
    private static final String UPSERT =
        "insert into ai_message_feedback (company_id, message_id, rating, correction, created_by) "
            + "select ?, m.id, ?, ?, ? from messages m where m.id = ? and m.company_id = ? "
            + "on conflict (message_id) do update set "
            + "rating = excluded.rating, correction = excluded.correction "
            + "returning (xmax = 0) as inserted";

    // Lista o feedback da empresa, mais recente primeiro, com o conteúdo da mensagem da IA
    // juntado (para a tela de "revisar respostas ruins"). Limite fixo de 50 (a view é de curadoria,
    // não paginação completa). Duas variantes (com/sem filtro de rating) em vez de um bind nullable
    // com cast ::text — evita a ambiguidade de tipo do setNull do JdbcTemplate, sem precedente no repo.
    private static final String LIST_BASE =
        "select f.id, f.message_id, f.rating, f.correction, f.created_at, m.content as message_content "
            + "from ai_message_feedback f join messages m on m.id = f.message_id "
            + "where f.company_id = ? ";

    private static final String LIST_ALL =
        LIST_BASE + "order by f.created_at desc limit 50";

    private static final String LIST_BY_RATING =
        LIST_BASE + "and f.rating = ? order by f.created_at desc limit 50";

    private static final RowMapper<Map<String, Object>> ROW_MAPPER = (rs, rowNum) -> {
        Map<String, Object> m = new HashMap<>();
        m.put("id", rs.getObject("id").toString());
        m.put("messageId", rs.getObject("message_id").toString());
        m.put("rating", rs.getString("rating"));
        m.put("correction", rs.getString("correction"));
        m.put("messageContent", rs.getString("message_content"));
        m.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
        return m;
    };

    private final JdbcTemplate jdbcTemplate;

    public FeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Desfecho do upsert, para o controller mapear o status HTTP. */
    public enum UpsertResult {
        /** Feedback novo criado → 201. */
        INSERTED,
        /** Feedback já existia → atualizado → 200. */
        UPDATED,
        /** A mensagem não existe ou não é da empresa do tenant → 404 (isolamento). */
        MESSAGE_NOT_FOUND,
    }

    /**
     * Upsert do feedback de uma mensagem da IA. Cria se não existe, atualiza rating/correção se
     * já existe (UNIQUE por message_id). Só age sobre mensagem da PRÓPRIA empresa — referência a
     * mensagem de outro tenant (ou inexistente) devolve {@link UpsertResult#MESSAGE_NOT_FOUND}
     * (o INSERT é guardado por um SELECT da messages por company_id — ver SQL UPSERT).
     *
     * @param companyId  empresa do authenticatedUser (isolamento)
     * @param messageId  mensagem da IA avaliada (FK messages)
     * @param rating     'good' ou 'bad' (CHECK do schema revalida)
     * @param correction correção opcional do tenant (nullable)
     * @param createdBy  auth.users.id de quem deu o feedback (nullable; FK ON DELETE SET NULL)
     * @return INSERTED (201), UPDATED (200) ou MESSAGE_NOT_FOUND (404)
     */
    public UpsertResult upsert(UUID companyId, UUID messageId, String rating,
                               String correction, UUID createdBy) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(rating, "rating must not be null");
        // Bind na ordem do INSERT ... SELECT: company_id, rating, correction, created_by,
        // message_id (do WHERE), company_id (do WHERE). RETURNING vem vazio quando o SELECT não
        // casa (mensagem inexistente/de outra empresa) → MESSAGE_NOT_FOUND.
        List<Boolean> rows = jdbcTemplate.query(
            UPSERT, (rs, rowNum) -> rs.getBoolean("inserted"),
            companyId, rating, correction, createdBy, messageId, companyId);
        if (rows.isEmpty()) {
            return UpsertResult.MESSAGE_NOT_FOUND;
        }
        return rows.get(0) ? UpsertResult.INSERTED : UpsertResult.UPDATED;
    }

    /**
     * Lista o feedback da empresa (mais recentes 50), com o conteúdo da mensagem juntado, para a
     * tela de revisão. {@code rating} opcional filtra (ex.: "bad" para revisar só as respostas
     * ruins); null traz todos.
     *
     * @param companyId empresa do authenticatedUser
     * @param rating    filtro de rating ('good'|'bad'), ou null para todos
     * @return linhas em camelCase (id, messageId, rating, correction, messageContent, createdAt)
     */
    public List<Map<String, Object>> list(UUID companyId, String rating) {
        Objects.requireNonNull(companyId, "companyId must not be null");
        if (rating == null) {
            return jdbcTemplate.query(LIST_ALL, ROW_MAPPER, companyId);
        }
        return jdbcTemplate.query(LIST_BY_RATING, ROW_MAPPER, companyId, rating);
    }
}
