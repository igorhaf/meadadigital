package com.meada.whatsapp.profiles.estetica.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/** Acesso a {@code aesthetic_config} (camada 8.3). 1:1 com company. Ausente → defaults. service_role. */
@Repository
public class AestheticConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AestheticConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AestheticConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, slot_minutes from aesthetic_config where company_id = ?",
                (rs, rn) -> new AestheticConfig(companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getInt("slot_minutes")),
                companyId)
            .stream().findFirst().orElse(AestheticConfig.defaultFor(companyId));
    }

    public AestheticConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, int slotMinutes) {
        jdbcTemplate.update(
            "insert into aesthetic_config (company_id, opens_at, closes_at, slot_minutes) values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set opens_at = excluded.opens_at, "
                + "closes_at = excluded.closes_at, slot_minutes = excluded.slot_minutes, updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), slotMinutes);
        return findByCompany(companyId);
    }
}
