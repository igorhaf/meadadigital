package com.meada.profiles.viagens.config;

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
                "select business_name, notes, trip_reminder_enabled, quote_followup_enabled, "
                    + "quote_followup_days from travel_config where company_id = ?",
                (rs, rn) -> new TravelConfig(companyId, rs.getString("business_name"), rs.getString("notes"),
                    rs.getBoolean("trip_reminder_enabled"), rs.getBoolean("quote_followup_enabled"),
                    rs.getInt("quote_followup_days")),
                companyId)
            .stream().findFirst().orElse(TravelConfig.defaultFor(companyId));
    }

    public TravelConfig upsert(UUID companyId, String businessName, String notes,
                               boolean tripReminderEnabled, boolean quoteFollowupEnabled,
                               int quoteFollowupDays) {
        jdbcTemplate.update(
            "insert into travel_config (company_id, business_name, notes, trip_reminder_enabled, "
                + "quote_followup_enabled, quote_followup_days) values (?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, trip_reminder_enabled = excluded.trip_reminder_enabled, "
                + "quote_followup_enabled = excluded.quote_followup_enabled, "
                + "quote_followup_days = excluded.quote_followup_days, updated_at = now()",
            companyId, businessName, notes, tripReminderEnabled, quoteFollowupEnabled, quoteFollowupDays);
        return findByCompany(companyId);
    }
}
