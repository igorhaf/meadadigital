package com.meada.profiles.comida.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregações do relatório de vendas do comida (onda 1, backlog #15). service_role. Faturamento =
 * pedidos ENTREGUES, valor LÍQUIDO (total_cents já embute desconto e taxa). Ticket médio, top itens
 * (por qtd, dos entregues) e horário de pico (pedidos criados por hora local, America/Sao_Paulo).
 */
@Repository
public class ComidaReportsRepository {

    private final JdbcTemplate jdbcTemplate;

    public ComidaReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Totals(long count, long totalCents, long avgTicketCents) {}

    public Totals totals(UUID companyId, Instant since) {
        return jdbcTemplate.queryForObject(
            "select count(*) as n, coalesce(sum(total_cents), 0) as revenue, "
                + "coalesce(avg(total_cents), 0)::bigint as avg_ticket "
                + "from comida_orders where company_id = ? and status = 'entregue' and created_at >= ?",
            (rs, rn) -> new Totals(rs.getLong("n"), rs.getLong("revenue"), rs.getLong("avg_ticket")),
            companyId, Timestamp.from(since));
    }

    /** Faturamento entregue por mês (yyyy-MM, fuso America/Sao_Paulo). */
    public List<Map<String, Object>> byMonth(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select to_char(created_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as month, "
                + "count(*) as n, coalesce(sum(total_cents), 0) as revenue "
                + "from comida_orders where company_id = ? and status = 'entregue' and created_at >= ? "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "month", rs.getString("month"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Top itens dos pedidos entregues (por quantidade), com receita da linha (unit × qtd). */
    public List<Map<String, Object>> topItems(UUID companyId, Instant since, int limit) {
        return jdbcTemplate.query(
            "select i.item_name_snapshot as item, sum(i.qtd) as qtd, "
                + "coalesce(sum(i.qtd::bigint * i.unit_price_cents), 0) as revenue "
                + "from comida_order_items i join comida_orders o on o.id = i.order_id "
                + "where o.company_id = ? and o.status = 'entregue' and o.created_at >= ? "
                + "group by i.item_name_snapshot order by qtd desc limit ?",
            (rs, rn) -> Map.<String, Object>of(
                "item", rs.getString("item"),
                "count", rs.getLong("qtd"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since), limit);
    }

    /** Pedidos criados por HORA local (0..23) — horário de pico (todos os status, é demanda). */
    public List<Map<String, Object>> byHour(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select extract(hour from created_at at time zone 'America/Sao_Paulo')::int as hour, "
                + "count(*) as n "
                + "from comida_orders where company_id = ? and created_at >= ? "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "hour", rs.getInt("hour"),
                "count", rs.getLong("n")),
            companyId, Timestamp.from(since));
    }
}
