package com.meada.whatsapp.profiles.pousada.rooms;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code pousada_rooms} (camada 7.6). Opera via service_role; escopo por company_id.
 */
@Repository
public class PousadaRoomRepository {

    private static final RowMapper<PousadaRoom> MAPPER = (rs, rn) -> new PousadaRoom(
        (UUID) rs.getObject("id"),
        rs.getString("name"),
        rs.getInt("capacity"),
        rs.getInt("nightly_rate_cents"),
        rs.getString("description"),
        rs.getBoolean("active"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS =
        "id, name, capacity, nightly_rate_cents, description, active, notes, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public PousadaRoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PousadaRoom> listByCompany(UUID companyId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(
            "select " + COLS + " from pousada_rooms where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (onlyActive) {
            sql.append(" and active = true");
        }
        sql.append(" order by nightly_rate_cents asc, name asc");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<PousadaRoom> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from pousada_rooms where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public PousadaRoom insert(UUID companyId, String name, int capacity, int nightlyRateCents,
                              String description, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into pousada_rooms (company_id, name, capacity, nightly_rate_cents, description, notes) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), capacity, nightlyRateCents, description, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<PousadaRoom> update(UUID companyId, UUID id, String name, Integer capacity,
                                        Integer nightlyRateCents, String description, String notes,
                                        Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (capacity != null) { sets.add("capacity = ?"); args.add(capacity); }
        if (nightlyRateCents != null) { sets.add("nightly_rate_cents = ?"); args.add(nightlyRateCents); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update pousada_rooms set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public Optional<PousadaRoom> toggle(UUID companyId, UUID id, boolean active) {
        int n = jdbcTemplate.update(
            "update pousada_rooms set active = ?, updated_at = now() where company_id = ? and id = ?",
            active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from pousada_rooms where company_id = ? and id = ?", companyId, id) > 0;
    }
}
