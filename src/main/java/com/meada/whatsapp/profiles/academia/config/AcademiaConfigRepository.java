package com.meada.whatsapp.profiles.academia.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code academia_config} (camada 7.7). 1:1 com company. Ausente → defaults. service_role.
 */
@Repository
public class AcademiaConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AcademiaConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AcademiaConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at from academia_config where company_id = ?",
                (rs, rn) -> new AcademiaConfig(
                    companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class)),
                companyId)
            .stream().findFirst().orElse(AcademiaConfig.defaultFor(companyId));
    }

    public AcademiaConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt) {
        jdbcTemplate.update(
            "insert into academia_config (company_id, opens_at, closes_at) values (?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt));
        return findByCompany(companyId);
    }
}
