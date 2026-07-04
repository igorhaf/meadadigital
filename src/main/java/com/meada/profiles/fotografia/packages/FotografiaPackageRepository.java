package com.meada.profiles.fotografia.packages;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code fotografia_packages} (camada 8.16). service_role; escopo por company_id. Espelho do DermatologiaProcedureTypeRepository com preço + delivery_days. */
@Repository
public class FotografiaPackageRepository {

    private static final RowMapper<FotografiaPackage> MAPPER = (rs, rn) -> new FotografiaPackage(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("category"),
        rs.getInt("duration_minutes"),
        rs.getInt("price_cents"),
        rs.getInt("delivery_days"),
        rs.getBoolean("active"),
        rs.getBoolean("suggestible"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, category, duration_minutes, price_cents, delivery_days, active, suggestible, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public FotografiaPackageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FotografiaPackage> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from fotografia_packages where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<FotografiaPackage> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from fotografia_packages where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public FotografiaPackage insert(UUID companyId, String name, String category, int durationMinutes,
                                    int priceCents, int deliveryDays, String notes, boolean suggestible) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into fotografia_packages (company_id, name, category, duration_minutes, price_cents, delivery_days, notes, suggestible) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), category, durationMinutes, priceCents, deliveryDays, notes, suggestible);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<FotografiaPackage> update(UUID companyId, UUID id, String name, String category,
                                              Integer durationMinutes, Integer priceCents, Integer deliveryDays,
                                              String notes, Boolean active, Boolean suggestible) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (category != null) { sets.add("category = ?"); args.add(category); }
        if (durationMinutes != null) { sets.add("duration_minutes = ?"); args.add(durationMinutes); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (deliveryDays != null) { sets.add("delivery_days = ?"); args.add(deliveryDays); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (suggestible != null) { sets.add("suggestible = ?"); args.add(suggestible); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update fotografia_packages set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<FotografiaPackage> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update("update fotografia_packages set active = ?, updated_at = now() "
            + "where company_id = ? and id = ?", active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from fotografia_packages where company_id = ? and id = ?", companyId, id) > 0;
    }
}
