package com.meada.profiles.cursos.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Acesso a {@code cursos_config} (camada 8.20 / perfil cursos). 1:1 com company. Ausente → defaults.
 * service_role. Análogo ao AcademiaConfigRepository (camada 7.7) com o campo extra {@code notes}.
 */
@Repository
public class CursosConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public CursosConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CursosConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select opens_at, closes_at, notes, nudge_enabled, nudge_days, certificate_base_url "
                    + "from cursos_config where company_id = ?",
                (rs, rn) -> new CursosConfig(
                    companyId,
                    rs.getObject("opens_at", LocalTime.class),
                    rs.getObject("closes_at", LocalTime.class),
                    rs.getString("notes"),
                    rs.getBoolean("nudge_enabled"),
                    rs.getInt("nudge_days"),
                    rs.getString("certificate_base_url")),
                companyId)
            .stream().findFirst().orElse(CursosConfig.defaultFor(companyId));
    }

    public CursosConfig upsert(UUID companyId, LocalTime opensAt, LocalTime closesAt, String notes,
                               boolean nudgeEnabled, int nudgeDays, String certificateBaseUrl) {
        jdbcTemplate.update(
            "insert into cursos_config (company_id, opens_at, closes_at, notes, nudge_enabled, "
                + "nudge_days, certificate_base_url) values (?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set "
                + "opens_at = excluded.opens_at, closes_at = excluded.closes_at, notes = excluded.notes, "
                + "nudge_enabled = excluded.nudge_enabled, nudge_days = excluded.nudge_days, "
                + "certificate_base_url = excluded.certificate_base_url, "
                + "updated_at = now()",
            companyId, Time.valueOf(opensAt), Time.valueOf(closesAt), notes, nudgeEnabled, nudgeDays,
            certificateBaseUrl);
        return findByCompany(companyId);
    }
}
