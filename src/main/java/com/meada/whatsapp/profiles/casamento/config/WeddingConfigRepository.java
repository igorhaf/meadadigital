package com.meada.whatsapp.profiles.casamento.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code wedding_config} (camada 8.7). 1:1 com company. Apenas nome da assessoria + notas
 * (SEM horário/slot — não há agenda). Ausente → defaults (vazios). service_role. Espelho do
 * EventConfigRepository.
 */
@Repository
public class WeddingConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public WeddingConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WeddingConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes from wedding_config where company_id = ?",
                (rs, rn) -> new WeddingConfig(companyId, rs.getString("business_name"), rs.getString("notes")),
                companyId)
            .stream().findFirst().orElse(WeddingConfig.defaultFor(companyId));
    }

    public WeddingConfig upsert(UUID companyId, String businessName, String notes) {
        jdbcTemplate.update(
            "insert into wedding_config (company_id, business_name, notes) values (?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, updated_at = now()",
            companyId, businessName, notes);
        return findByCompany(companyId);
    }
}
