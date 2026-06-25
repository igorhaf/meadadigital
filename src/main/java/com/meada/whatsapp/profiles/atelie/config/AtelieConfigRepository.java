package com.meada.whatsapp.profiles.atelie.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code atelie_config} (camada 8.14). 1:1 com company. Apenas nome do ateliê + notas (SEM
 * horário/slot — não há agenda). Ausente → defaults (vazios). service_role. Espelho do
 * EventConfigRepository.
 */
@Repository
public class AtelieConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtelieConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AtelieConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes from atelie_config where company_id = ?",
                (rs, rn) -> new AtelieConfig(companyId, rs.getString("business_name"), rs.getString("notes")),
                companyId)
            .stream().findFirst().orElse(AtelieConfig.defaultFor(companyId));
    }

    public AtelieConfig upsert(UUID companyId, String businessName, String notes) {
        jdbcTemplate.update(
            "insert into atelie_config (company_id, business_name, notes) values (?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, updated_at = now()",
            companyId, businessName, notes);
        return findByCompany(companyId);
    }
}
