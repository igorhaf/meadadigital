package com.meada.whatsapp.profiles.dermatologia.professionals;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code dermatologia_professionals} (camada 8.11). service_role; escopo por company_id. */
@Repository
public class DermatologiaProfessionalRepository {

    private static final RowMapper<DermatologiaProfessional> MAPPER = (rs, rn) -> new DermatologiaProfessional(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("specialty"),
        rs.getString("crm_rqe"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, specialty, crm_rqe, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public DermatologiaProfessionalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DermatologiaProfessional> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from dermatologia_professionals where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<DermatologiaProfessional> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from dermatologia_professionals where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public DermatologiaProfessional insert(UUID companyId, String name, String specialty, String crmRqe, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into dermatologia_professionals (company_id, name, specialty, crm_rqe, notes) values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), specialty, crmRqe, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<DermatologiaProfessional> update(UUID companyId, UUID id, String name, String specialty,
                                                     String crmRqe, String notes, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (specialty != null) { sets.add("specialty = ?"); args.add(specialty); }
        if (crmRqe != null) { sets.add("crm_rqe = ?"); args.add(crmRqe); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update dermatologia_professionals set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<DermatologiaProfessional> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update dermatologia_professionals set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from dermatologia_professionals where company_id = ? and id = ?", companyId, id) > 0;
    }
}
