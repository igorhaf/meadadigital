package com.meada.profiles.barbearia.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agregações do relatório da barbearia (onda 1, backlog #15). service_role. Faturamento = SÓ
 * agendamentos REALIZADOS, valor LÍQUIDO (coalesce(preço,0) − desconto — o corte grátis da
 * fidelidade fatura 0). Taxa de falta = faltas / (realizados + faltas). Janela sobre start_at.
 */
@Repository
public class BarberReportsRepository {

    private final JdbcTemplate jdbcTemplate;

    public BarberReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record Totals(long realized, long noShows, long cancelled, long totalCents) {}

    public Totals totals(UUID companyId, Instant since) {
        return jdbcTemplate.queryForObject(
            "select count(*) filter (where status = 'realizado') as realized, "
                + "count(*) filter (where status = 'falta') as no_shows, "
                + "count(*) filter (where status = 'cancelado') as cancelled, "
                + "coalesce(sum(coalesce(price_cents, 0) - discount_cents) "
                + "  filter (where status = 'realizado'), 0) as revenue "
                + "from barber_appointments where company_id = ? and start_at >= ?",
            (rs, rn) -> new Totals(rs.getLong("realized"), rs.getLong("no_shows"),
                rs.getLong("cancelled"), rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Uma linha por mês (yyyy-MM, America/Sao_Paulo) — realizados + faturamento líquido. */
    public List<Map<String, Object>> byMonth(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select to_char(start_at at time zone 'America/Sao_Paulo', 'YYYY-MM') as month, "
                + "count(*) as n, coalesce(sum(coalesce(price_cents, 0) - discount_cents), 0) as revenue "
                + "from barber_appointments where company_id = ? and status = 'realizado' and start_at >= ? "
                + "group by 1 order by 1 asc",
            (rs, rn) -> Map.<String, Object>of(
                "month", rs.getString("month"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }

    /** Por barbeiro (snapshot do nome): realizados + faturamento + faltas. */
    public List<Map<String, Object>> byBarber(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select barber_name, count(*) filter (where status = 'realizado') as n, "
                + "count(*) filter (where status = 'falta') as no_shows, "
                + "coalesce(sum(coalesce(price_cents, 0) - discount_cents) "
                + "  filter (where status = 'realizado'), 0) as revenue "
                + "from barber_appointments where company_id = ? and start_at >= ? "
                + "group by barber_name order by revenue desc",
            (rs, rn) -> {
                Map<String, Object> row = new HashMap<>();
                row.put("barberName", rs.getString("barber_name"));
                row.put("count", rs.getLong("n"));
                row.put("noShows", rs.getLong("no_shows"));
                row.put("totalCents", rs.getLong("revenue"));
                return row;
            },
            companyId, Timestamp.from(since));
    }

    /** Ranking de serviços (snapshot do nome): realizados + faturamento líquido. */
    public List<Map<String, Object>> byService(UUID companyId, Instant since) {
        return jdbcTemplate.query(
            "select service_name, count(*) as n, "
                + "coalesce(sum(coalesce(price_cents, 0) - discount_cents), 0) as revenue "
                + "from barber_appointments where company_id = ? and status = 'realizado' and start_at >= ? "
                + "group by service_name order by n desc, revenue desc",
            (rs, rn) -> Map.<String, Object>of(
                "serviceName", rs.getString("service_name"),
                "count", rs.getLong("n"),
                "totalCents", rs.getLong("revenue")),
            companyId, Timestamp.from(since));
    }
}
