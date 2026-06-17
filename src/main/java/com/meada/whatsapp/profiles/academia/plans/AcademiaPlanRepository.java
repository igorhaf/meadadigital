package com.meada.whatsapp.profiles.academia.plans;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_plans} (camada 7.7). Opera via service_role; escopo por company_id.
 */
@Repository
public class AcademiaPlanRepository {

    private static final RowMapper<AcademiaPlan> MAPPER = (rs, rn) -> new AcademiaPlan(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getInt("monthly_cents"),
        rs.getString("description"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, monthly_cents, description, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AcademiaPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AcademiaPlan> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from academia_plans where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by monthly_cents asc, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<AcademiaPlan> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from academia_plans where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public AcademiaPlan insert(UUID companyId, String name, int monthlyCents, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_plans (company_id, name, monthly_cents, description) "
                + "values (?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), monthlyCents, description);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<AcademiaPlan> update(UUID companyId, UUID id, String name, Integer monthlyCents,
                                         String description, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (monthlyCents != null) { sets.add("monthly_cents = ?"); args.add(monthlyCents); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update academia_plans set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<AcademiaPlan> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update academia_plans set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from academia_plans where company_id = ? and id = ?", companyId, id) > 0;
    }
}
