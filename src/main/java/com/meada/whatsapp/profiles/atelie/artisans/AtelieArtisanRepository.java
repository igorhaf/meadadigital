package com.meada.whatsapp.profiles.atelie.artisans;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code atelie_artisans} (camada 8.14). Opera via service_role; escopo por company_id.
 * Espelho do EventPlannerRepository.
 */
@Repository
public class AtelieArtisanRepository {

    private static final RowMapper<AtelieArtisan> MAPPER = (rs, rn) -> new AtelieArtisan(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("specialty"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, name, specialty, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public AtelieArtisanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AtelieArtisan> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from atelie_artisans where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<AtelieArtisan> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from atelie_artisans where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public AtelieArtisan insert(UUID companyId, String name, String specialty, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into atelie_artisans (company_id, name, specialty, notes) values (?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), specialty, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<AtelieArtisan> update(UUID companyId, UUID id, String name, String specialty,
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
            int n = jdbcTemplate.update("update atelie_artisans set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<AtelieArtisan> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update atelie_artisans set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from atelie_artisans where company_id = ? and id = ?", companyId, id) > 0;
    }

    /**
     * True se o artesão está atribuído a alguma proposta. atelie_proposals.artisan_id é ON DELETE SET
     * NULL (não restrict), então a FK não barra o delete — checamos explicitamente para devolver 409
     * artisan_in_use em vez de silenciosamente desvincular o histórico.
     */
    public boolean hasProposals(UUID companyId, UUID id) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from atelie_proposals where company_id = ? and artisan_id = ?",
            Integer.class, companyId, id);
        return n != null && n > 0;
    }
}
