package com.meada.whatsapp.profiles.escola.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code escola_config} (camada 8.19). 1:1 com company. Ausente → defaults (07:00/18:00).
 * service_role.
 */
@Repository
public class EscolaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public EscolaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EscolaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, opens_at, closes_at, notes from escola_config where company_id = ?",
                (rs, rn) -> new EscolaConfig(
                    companyId,
                    rs.getString("business_name"),
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getString("notes")),
                companyId)
            .stream().findFirst().orElse(EscolaConfig.defaultFor(companyId));
    }

    public EscolaConfig upsert(UUID companyId, String businessName, LocalTime opensAt, LocalTime closesAt,
                               String notes) {
        jdbcTemplate.update(
            "insert into escola_config (company_id, business_name, opens_at, closes_at, notes) "
                + "values (?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, notes = excluded.notes, "
                + "updated_at = now()",
            companyId, businessName, Time.valueOf(opensAt), Time.valueOf(closesAt), notes);
        return findByCompany(companyId);
    }
}
