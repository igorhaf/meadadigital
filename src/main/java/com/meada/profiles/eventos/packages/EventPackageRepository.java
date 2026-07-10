package com.meada.profiles.eventos.packages;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code event_packages} (onda Eventos 1). service_role; escopo por company no WHERE. */
@Repository
public class EventPackageRepository {

    private static final RowMapper<EventPackage> MAPPER = (rs, rn) -> new EventPackage(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getString("kind"),
        rs.getString("description"),
        rs.getInt("price_cents"),
        rs.getBoolean("suggestible"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, kind, description, price_cents, suggestible, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public EventPackageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EventPackage> listByCompany(UUID companyId, boolean onlyActive) {
        return jdbcTemplate.query(
            "select " + COLS + " from event_packages where company_id = ?"
                + (onlyActive ? " and active = true" : "")
                + " order by kind, name",
            MAPPER, companyId);
    }

    public Optional<EventPackage> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from event_packages where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public EventPackage insert(UUID companyId, String name, String kind, String description,
                               int priceCents, boolean suggestible, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into event_packages (company_id, name, kind, description, price_cents, suggestible, active) "
                + "values (?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), kind, description, priceCents, suggestible, active);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<EventPackage> update(UUID companyId, UUID id, String name, String kind,
                                         String description, Integer priceCents, Boolean suggestible,
                                         Boolean active) {
        java.util.List<String> sets = new java.util.ArrayList<>();
        java.util.List<Object> args = new java.util.ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (description != null) { sets.add("description = ?"); args.add(description.isBlank() ? null : description); }
        if (priceCents != null) { sets.add("price_cents = ?"); args.add(priceCents); }
        if (suggestible != null) { sets.add("suggestible = ?"); args.add(suggestible); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update event_packages set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from event_packages where company_id = ? and id = ?",
            companyId, id) > 0;
    }
}
