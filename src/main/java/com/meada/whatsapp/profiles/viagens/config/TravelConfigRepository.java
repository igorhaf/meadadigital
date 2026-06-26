package com.meada.whatsapp.profiles.viagens.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code travel_config} (camada 8.18 / perfil viagens). 1:1 com company. Apenas nome da
 * agência + notas (SEM horário/slot — não há agenda). Ausente → defaults (vazios). service_role.
 * Espelho EXATO do EventConfigRepository (chassi eventos 8.2).
 */
@Repository
public class TravelConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public TravelConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public TravelConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes from travel_config where company_id = ?",
                (rs, rn) -> new TravelConfig(companyId, rs.getString("business_name"), rs.getString("notes")),
                companyId)
            .stream().findFirst().orElse(TravelConfig.defaultFor(companyId));
    }

    public TravelConfig upsert(UUID companyId, String businessName, String notes) {
        jdbcTemplate.update(
            "insert into travel_config (company_id, business_name, notes) values (?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, updated_at = now()",
            companyId, businessName, notes);
        return findByCompany(companyId);
    }
}
