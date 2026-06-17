package com.meada.whatsapp.profiles.pet.services;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code pet_services} (camada 7.8). Opera via service_role; escopo por company_id.
 */
@Repository
public class PetServiceRepository {

    private static final RowMapper<PetService> MAPPER = (rs, rn) -> new PetService(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getInt("duration_minutes"),
        (Integer) rs.getObject("price_cents"),
        rs.getString("species_restriction"),
        rs.getBoolean("active"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, category, duration_minutes, price_cents, species_restriction, active, description, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public PetServiceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PetService> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from pet_services where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by category asc nulls last, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<PetService> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from pet_services where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public PetService insert(UUID companyId, String name, String category, int durationMinutes,
                             Integer priceCents, String speciesRestriction, String description) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into pet_services (company_id, name, category, duration_minutes, price_cents, species_restriction, description) "
                + "values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), category, durationMinutes, priceCents, speciesRestriction, description);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<PetService> update(UUID companyId, UUID id, String name, String category,
                                       Integer durationMinutes, Integer priceCents, String speciesRestriction,
                                       boolean speciesProvided, String description, Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (category != null) { sets.add("category = ?"); args.add(category); }
        if (durationMinutes != null) { sets.add("duration_minutes = ?"); args.add(durationMinutes); }
        if (priceCents != null) {
            if (priceCents < 0) { sets.add("price_cents = null"); }
            else { sets.add("price_cents = ?"); args.add(priceCents); }
        }
        // species: só altera se o caller sinalizou (permite setar null explicitamente).
        if (speciesProvided) {
            if (speciesRestriction == null || speciesRestriction.isBlank()) { sets.add("species_restriction = null"); }
            else { sets.add("species_restriction = ?"); args.add(speciesRestriction); }
        }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update pet_services set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<PetService> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update pet_services set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from pet_services where company_id = ? and id = ?", companyId, id) > 0;
    }
}
