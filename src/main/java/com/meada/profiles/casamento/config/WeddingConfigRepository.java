package com.meada.profiles.casamento.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code wedding_config} (camada 8.7). 1:1 com company. Nome da assessoria + notas + toggles
 * da onda 1 (lembretes/auto-realizada/aniversário). Ausente → defaults (vazios, toggles ligados).
 * service_role. Espelho do EventConfigRepository.
 */
@Repository
public class WeddingConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public WeddingConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WeddingConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes, checklist_reminder_enabled, payment_reminder_enabled, "
                    + "auto_complete_enabled, anniversary_enabled, post_event_enabled, review_link, "
                    + "follow_up_enabled, follow_up_days from wedding_config where company_id = ?",
                (rs, rn) -> new WeddingConfig(companyId, rs.getString("business_name"), rs.getString("notes"),
                    rs.getBoolean("checklist_reminder_enabled"), rs.getBoolean("payment_reminder_enabled"),
                    rs.getBoolean("auto_complete_enabled"), rs.getBoolean("anniversary_enabled"),
                    rs.getBoolean("post_event_enabled"), rs.getString("review_link"),
                    rs.getBoolean("follow_up_enabled"), rs.getInt("follow_up_days")),
                companyId)
            .stream().findFirst().orElse(WeddingConfig.defaultFor(companyId));
    }

    public WeddingConfig upsert(UUID companyId, String businessName, String notes,
                                boolean checklistReminderEnabled, boolean paymentReminderEnabled,
                                boolean autoCompleteEnabled, boolean anniversaryEnabled,
                                boolean postEventEnabled, String reviewLink,
                                boolean followUpEnabled, int followUpDays) {
        jdbcTemplate.update(
            "insert into wedding_config (company_id, business_name, notes, checklist_reminder_enabled, "
                + "payment_reminder_enabled, auto_complete_enabled, anniversary_enabled, "
                + "post_event_enabled, review_link, follow_up_enabled, follow_up_days) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, checklist_reminder_enabled = excluded.checklist_reminder_enabled, "
                + "payment_reminder_enabled = excluded.payment_reminder_enabled, "
                + "auto_complete_enabled = excluded.auto_complete_enabled, "
                + "anniversary_enabled = excluded.anniversary_enabled, "
                + "post_event_enabled = excluded.post_event_enabled, "
                + "review_link = excluded.review_link, "
                + "follow_up_enabled = excluded.follow_up_enabled, "
                + "follow_up_days = excluded.follow_up_days, updated_at = now()",
            companyId, businessName, notes, checklistReminderEnabled, paymentReminderEnabled,
            autoCompleteEnabled, anniversaryEnabled, postEventEnabled, reviewLink,
            followUpEnabled, followUpDays);
        return findByCompany(companyId);
    }
}
