package com.meada.profiles.atelie.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code atelie_config} (camada 8.14). 1:1 com company. Nome do ateliê + notas + toggle do
 * lembrete de prova/ajuste (SEM horário/slot — não há agenda). Ausente → defaults (vazios, lembrete
 * ligado). service_role. Espelho do EventConfigRepository.
 */
@Repository
public class AtelieConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtelieConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AtelieConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes, fitting_reminder_enabled, post_delivery_enabled, "
                    + "review_link, reactivation_enabled, reactivation_days "
                    + "from atelie_config where company_id = ?",
                (rs, rn) -> new AtelieConfig(companyId, rs.getString("business_name"), rs.getString("notes"),
                    rs.getBoolean("fitting_reminder_enabled"), rs.getBoolean("post_delivery_enabled"),
                    rs.getString("review_link"), rs.getBoolean("reactivation_enabled"),
                    rs.getInt("reactivation_days")),
                companyId)
            .stream().findFirst().orElse(AtelieConfig.defaultFor(companyId));
    }

    public AtelieConfig upsert(UUID companyId, String businessName, String notes,
                               boolean fittingReminderEnabled, boolean postDeliveryEnabled,
                               String reviewLink, boolean reactivationEnabled, int reactivationDays) {
        jdbcTemplate.update(
            "insert into atelie_config (company_id, business_name, notes, fitting_reminder_enabled, "
                + "post_delivery_enabled, review_link, reactivation_enabled, reactivation_days) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, fitting_reminder_enabled = excluded.fitting_reminder_enabled, "
                + "post_delivery_enabled = excluded.post_delivery_enabled, "
                + "review_link = excluded.review_link, "
                + "reactivation_enabled = excluded.reactivation_enabled, "
                + "reactivation_days = excluded.reactivation_days, updated_at = now()",
            companyId, businessName, notes, fittingReminderEnabled, postDeliveryEnabled, reviewLink,
            reactivationEnabled, reactivationDays);
        return findByCompany(companyId);
    }
}
