package com.meada.profiles.las.yield;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso a {@code las_yield_reference} (onda Lãs 1). service_role; escopo por company no WHERE. */
@Repository
public class LasYieldReferenceRepository {

    private static final RowMapper<LasYieldReference> MAPPER = (rs, rn) -> new LasYieldReference(
        (UUID) rs.getObject("id"),
        rs.getString("piece_type"),
        rs.getString("yarn_spec"),
        rs.getInt("skeins"),
        rs.getString("notes"),
        rs.getBoolean("active"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String SELECT =
        "select id, piece_type, yarn_spec, skeins, notes, active, created_at, updated_at "
            + "from las_yield_reference ";

    private final JdbcTemplate jdbcTemplate;

    public LasYieldReferenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LasYieldReference> listByCompany(UUID companyId, boolean onlyActive) {
        return jdbcTemplate.query(
            SELECT + "where company_id = ?" + (onlyActive ? " and active = true" : "")
                + " order by piece_type, created_at",
            MAPPER, companyId);
    }

    public Optional<LasYieldReference> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(SELECT + "where company_id = ? and id = ?", MAPPER, companyId, id)
            .stream().findFirst();
    }

    public LasYieldReference insert(UUID companyId, String pieceType, String yarnSpec, int skeins,
                                    String notes, boolean active) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into las_yield_reference (company_id, piece_type, yarn_spec, skeins, notes, active) "
                + "values (?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, pieceType, yarnSpec, skeins, notes, active);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<LasYieldReference> update(UUID companyId, UUID id, String pieceType, String yarnSpec,
                                              Integer skeins, String notes, Boolean active) {
        int n = jdbcTemplate.update(
            "update las_yield_reference set "
                + "piece_type = coalesce(?, piece_type), "
                + "yarn_spec = ?, "
                + "skeins = coalesce(?, skeins), "
                + "notes = ?, "
                + "active = coalesce(?, active), updated_at = now() "
                + "where company_id = ? and id = ?",
            pieceType, yarnSpec, skeins, notes, active, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from las_yield_reference where company_id = ? and id = ?",
            companyId, id) > 0;
    }
}
