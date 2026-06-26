package com.meada.whatsapp.profiles.dermatologia.proceduretypes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code dermatologia_procedure_types} (camada 8.11, ESCAPADA). service_role; escopo por company_id. */
@Repository
public class DermatologiaProcedureTypeRepository {

    private static final RowMapper<DermatologiaProcedureType> MAPPER = (rs, rn) -> new DermatologiaProcedureType(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getInt("duration_minutes"),
        rs.getString("prep_instructions"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, duration_minutes, prep_instructions, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public DermatologiaProcedureTypeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DermatologiaProcedureType> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from dermatologia_procedure_types where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<DermatologiaProcedureType> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from dermatologia_procedure_types where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public DermatologiaProcedureType insert(UUID companyId, String name, int durationMinutes, String prepInstructions, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into dermatologia_procedure_types (company_id, name, duration_minutes, prep_instructions, notes) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), durationMinutes, prepInstructions, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<DermatologiaProcedureType> update(UUID companyId, UUID id, String name, Integer durationMinutes,
                                                      String prepInstructions, boolean prepProvided, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (durationMinutes != null) { sets.add("duration_minutes = ?"); args.add(durationMinutes); }
        if (prepProvided) { sets.add("prep_instructions = ?"); args.add(prepInstructions); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update dermatologia_procedure_types set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<DermatologiaProcedureType> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update dermatologia_procedure_types set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from dermatologia_procedure_types where company_id = ? and id = ?", companyId, id) > 0;
    }
}
