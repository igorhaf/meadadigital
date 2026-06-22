package com.meada.whatsapp.profiles.estetica.procedures;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code aesthetic_procedures} (camada 8.3). service_role; escopo por company_id. */
@Repository
public class AestheticProcedureRepository {

    private static final RowMapper<AestheticProcedure> MAPPER = (rs, rn) -> new AestheticProcedure(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getInt("duration_minutes"),
        rs.getInt("unit_price_cents"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, category, duration_minutes, unit_price_cents, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AestheticProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AestheticProcedure> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from aesthetic_procedures where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<AestheticProcedure> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from aesthetic_procedures where company_id = ? and id = ?",
                MAPPER, companyId, id).stream().findFirst();
    }

    public AestheticProcedure insert(UUID companyId, String name, String category, int durationMinutes,
                                     int unitPriceCents, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into aesthetic_procedures (company_id, name, category, duration_minutes, unit_price_cents, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), category, durationMinutes, unitPriceCents, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<AestheticProcedure> update(UUID companyId, UUID id, String name, String category,
                                               Integer durationMinutes, Integer unitPriceCents,
                                               String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (category != null) { sets.add("category = ?"); args.add(category); }
        if (durationMinutes != null) { sets.add("duration_minutes = ?"); args.add(durationMinutes); }
        if (unitPriceCents != null) { sets.add("unit_price_cents = ?"); args.add(unitPriceCents); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update aesthetic_procedures set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<AestheticProcedure> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update aesthetic_procedures set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. FK restrict de appointments E packages barra se houver uso → DataIntegrityViolation. */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from aesthetic_procedures where company_id = ? and id = ?", companyId, id) > 0;
    }
}
