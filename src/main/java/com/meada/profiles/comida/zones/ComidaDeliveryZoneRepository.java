package com.meada.profiles.comida.zones;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code comida_delivery_zones} (onda 1, backlog #8). service_role; escopo por company_id.
 * {@link #findActiveById} é a resolução da taxa na criação do pedido (zona inativa não vale).
 */
@Repository
public class ComidaDeliveryZoneRepository {

    private static final RowMapper<ComidaDeliveryZone> MAPPER = (rs, rn) -> new ComidaDeliveryZone(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("name"),
        rs.getInt("fee_cents"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String COLS = "id, company_id, name, fee_cents, active, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    public ComidaDeliveryZoneRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ComidaDeliveryZone> listByCompany(UUID companyId, boolean onlyActive) {
        String sql = "select " + COLS + " from comida_delivery_zones where company_id = ?"
            + (onlyActive ? " and active = true" : "")
            + " order by name asc";
        return jdbcTemplate.query(sql, MAPPER, companyId);
    }

    public Optional<ComidaDeliveryZone> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(
                "select " + COLS + " from comida_delivery_zones where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Resolução da taxa na criação do pedido: só zona ATIVA vale (senão fallback flat). */
    public Optional<ComidaDeliveryZone> findActiveById(UUID companyId, UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                "select " + COLS + " from comida_delivery_zones where company_id = ? and id = ? and active = true",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public ComidaDeliveryZone insert(UUID companyId, String name, int feeCents, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into comida_delivery_zones (company_id, name, fee_cents, active) "
                + "values (?, ?, ?, ?) returning id",
            UUID.class, companyId, name.trim(), feeCents, active);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<ComidaDeliveryZone> update(UUID companyId, UUID id, String name, Integer feeCents,
                                               Boolean active) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (name != null && !name.isBlank()) { sets.add("name = ?"); args.add(name.trim()); }
        if (feeCents != null) { sets.add("fee_cents = ?"); args.add(feeCents); }
        if (active != null) { sets.add("active = ?"); args.add(active); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update comida_delivery_zones set " + String.join(", ", sets)
                    + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update(
            "delete from comida_delivery_zones where company_id = ? and id = ?", companyId, id) > 0;
    }
}
