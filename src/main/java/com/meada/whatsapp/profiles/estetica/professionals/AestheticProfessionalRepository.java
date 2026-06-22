package com.meada.whatsapp.profiles.estetica.professionals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code aesthetic_professionals} (camada 8.3). service_role; escopo por company_id. Clone salon. */
@Repository
public class AestheticProfessionalRepository {

    private static final RowMapper<AestheticProfessional> MAPPER = (rs, rn) -> new AestheticProfessional(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("specialty"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, specialty, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AestheticProfessionalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AestheticProfessional> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from aesthetic_professionals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<AestheticProfessional> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from aesthetic_professionals where company_id = ? and id = ?",
                MAPPER, companyId, id).stream().findFirst();
    }

    public AestheticProfessional insert(UUID companyId, String name, String specialty, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into aesthetic_professionals (company_id, name, specialty, notes) values (?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), specialty, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<AestheticProfessional> update(UUID companyId, UUID id, String name, String specialty,
                                                  String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (specialty != null) { sets.add("specialty = ?"); args.add(specialty); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update aesthetic_professionals set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<AestheticProfessional> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update aesthetic_professionals set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    /** Hard delete. Lança DataIntegrityViolation se houver appointment referenciando (FK restrict). */
    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from aesthetic_professionals where company_id = ? and id = ?", companyId, id) > 0;
    }
}
