package com.meada.profiles.academia.reports;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de RELATÓRIO do tenant academia (docs #15). Opera via service_role; escopo por
 * company_id. SOMENTE LEITURA — nenhuma mutação.
 */
@Repository
public class AcademiaReportsRepository {

    private static final RowMapper<AcademiaSummaryReport> SUMMARY_MAPPER = (rs, rn) ->
        new AcademiaSummaryReport(
            rs.getLong("mrr_cents"),
            rs.getLong("active_count"),
            rs.getLong("suspended_count"),
            rs.getLong("canceled_count"));

    private static final RowMapper<AcademiaOccupancyRow> OCCUPANCY_MAPPER = (rs, rn) ->
        new AcademiaOccupancyRow(
            (UUID) rs.getObject("class_id"),
            rs.getString("class_name"),
            rs.getInt("day_of_week"),
            rs.getInt("capacity"),
            rs.getLong("active_count"));

    private final JdbcTemplate jdbcTemplate;

    public AcademiaReportsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * MRR (soma de plan_monthly_cents das matrículas ATIVAS) + contagem por status, numa passada.
     * FILTER garante um único SELECT sobre a tabela.
     */
    public AcademiaSummaryReport summary(UUID companyId) {
        return jdbcTemplate.queryForObject(
            "select "
                + "coalesce(sum(plan_monthly_cents) filter (where status = 'ativa'), 0) as mrr_cents, "
                + "count(*) filter (where status = 'ativa')     as active_count, "
                + "count(*) filter (where status = 'suspensa')  as suspended_count, "
                + "count(*) filter (where status = 'cancelada') as canceled_count "
                + "from academia_memberships where company_id = ?",
            SUMMARY_MAPPER, companyId);
    }

    /**
     * Ocupação por aula ATIVA: matrículas de status 'ativa' que ocupam a aula (via junction) x
     * capacidade. Ordenada por dia/horário da aula.
     */
    public List<AcademiaOccupancyRow> occupancy(UUID companyId) {
        return jdbcTemplate.query(
            "select c.id as class_id, c.name as class_name, c.day_of_week as day_of_week, "
                + "c.capacity as capacity, "
                + "(select count(*) from academia_membership_classes mc "
                + "   join academia_memberships m on m.id = mc.membership_id "
                + "  where mc.class_id = c.id and m.status = 'ativa') as active_count "
                + "from academia_classes c "
                + "where c.company_id = ? and c.active = true "
                + "order by c.day_of_week asc, c.start_time asc, c.name asc",
            OCCUPANCY_MAPPER, companyId);
    }
}
