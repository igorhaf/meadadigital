package com.meada.metrics;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lógica de consulta das métricas comparativas mês a mês (camada 5.23 #66/#65). Compartilhada
 * entre o {@link MetricsComparisonController} (que serve o JSON do dashboard) e o
 * {@link MetricsExportController} (que rende o PDF) — uma única fonte de verdade das contas.
 *
 * <p>Janelas de tempo calculadas no banco com {@code date_trunc('month', now())}:
 * <ul>
 *   <li>início do mês atual = {@code date_trunc('month', now())};
 *   <li>início do mês anterior = início do atual {@code - interval '1 month'}.
 * </ul>
 * Cada métrica é contada nesse intervalo {@code [início, fim)} por {@code created_at}, sempre
 * escopada por {@code company_id} (defesa em profundidade — o backend é service_role e não
 * aplica RLS). Os deltas são {@code atual - anterior} por métrica.
 *
 * <p>Métricas: conversas criadas, mensagens recebidas (inbound), mensagens enviadas (outbound)
 * e contatos ativos distintos (que tiveram ao menos uma mensagem no período).
 */
@Service
public class MetricsQueryService {

    private final JdbcTemplate jdbcTemplate;

    public MetricsQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retorna {current, previous, deltas} para a empresa: cada um um mapa camelCase com
     * conversations, messagesInbound, messagesOutbound, activeContacts. Pronto para virar JSON
     * (no controller de comparação) ou linhas de texto (no PDF).
     */
    public Map<String, Object> comparison(UUID companyId) {
        Map<String, Long> current = countsForMonthOffset(companyId, 0);
        Map<String, Long> previous = countsForMonthOffset(companyId, 1);
        Map<String, Long> deltas = new LinkedHashMap<>();
        for (String key : current.keySet()) {
            deltas.put(key, current.get(key) - previous.get(key));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("current", current);
        body.put("previous", previous);
        body.put("deltas", deltas);
        return body;
    }

    /**
     * Contagens de um mês calendário deslocado de {@code monthsAgo} (0 = atual, 1 = anterior).
     * A janela {@code [início, início + 1 mês)} é fechada-aberta para não dupla-contar a virada.
     */
    private Map<String, Long> countsForMonthOffset(UUID companyId, int monthsAgo) {
        // início do mês-alvo e início do mês seguinte (limite superior exclusivo).
        String monthStart = "date_trunc('month', now()) - make_interval(months => ?)";
        String nextMonthStart = "date_trunc('month', now()) - make_interval(months => ? - 1)";

        Long conversations = jdbcTemplate.queryForObject(
            "select count(*)::bigint from conversations "
                + "where company_id = ? and created_at >= " + monthStart
                + " and created_at < " + nextMonthStart,
            Long.class, companyId, monthsAgo, monthsAgo);

        Long inbound = jdbcTemplate.queryForObject(
            "select count(*)::bigint from messages "
                + "where company_id = ? and direction = 'inbound' and created_at >= " + monthStart
                + " and created_at < " + nextMonthStart,
            Long.class, companyId, monthsAgo, monthsAgo);

        Long outbound = jdbcTemplate.queryForObject(
            "select count(*)::bigint from messages "
                + "where company_id = ? and direction = 'outbound' and created_at >= " + monthStart
                + " and created_at < " + nextMonthStart,
            Long.class, companyId, monthsAgo, monthsAgo);

        // contatos ativos: distintos que tiveram alguma mensagem (em qualquer direção) no mês.
        Long activeContacts = jdbcTemplate.queryForObject(
            "select count(distinct c.contact_id)::bigint from messages m "
                + "join conversations c on c.id = m.conversation_id "
                + "where m.company_id = ? and m.created_at >= " + monthStart
                + " and m.created_at < " + nextMonthStart,
            Long.class, companyId, monthsAgo, monthsAgo);

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("conversations", conversations == null ? 0L : conversations);
        counts.put("messagesInbound", inbound == null ? 0L : inbound);
        counts.put("messagesOutbound", outbound == null ? 0L : outbound);
        counts.put("activeContacts", activeContacts == null ? 0L : activeContacts);
        return counts;
    }

    /** Nome da empresa (para o título do PDF). null se a empresa sumiu (não deveria). */
    public String companyName(UUID companyId) {
        return jdbcTemplate.query(
            "select name from companies where id = ?",
            rs -> rs.next() ? rs.getString("name") : null,
            companyId);
    }

    /** Top 10 contatos por número de mensagens da empresa (camada 5.23 #68). */
    public java.util.List<Map<String, Object>> topContacts(UUID companyId) {
        return jdbcTemplate.query(
            "select ct.id, ct.name, ct.phone_number, count(m.id)::bigint as message_count "
                + "from contacts ct "
                + "join conversations c on c.contact_id = ct.id "
                + "join messages m on m.conversation_id = c.id "
                + "where ct.company_id = ? and ct.deleted_at is null "
                + "group by ct.id, ct.name, ct.phone_number "
                + "order by message_count desc, ct.name asc nulls last "
                + "limit 10",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("contactId", rs.getObject("id").toString());
                row.put("name", rs.getString("name"));
                row.put("phoneNumber", rs.getString("phone_number"));
                row.put("messageCount", rs.getLong("message_count"));
                return row;
            },
            companyId);
    }
}
