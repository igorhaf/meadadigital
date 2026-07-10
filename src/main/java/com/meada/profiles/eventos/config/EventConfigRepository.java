package com.meada.profiles.eventos.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Acesso a {@code event_config} (camada 8.2). 1:1 com company. Apenas nome do espaço + notas (SEM
 * horário/slot — não há agenda). Ausente → defaults (vazios). service_role.
 */
@Repository
public class EventConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EventConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select business_name, notes, auto_complete_enabled, post_event_enabled, review_link, "
                    + "follow_up_enabled, follow_up_days from event_config where company_id = ?",
                (rs, rn) -> new EventConfig(companyId, rs.getString("business_name"), rs.getString("notes"),
                    rs.getBoolean("auto_complete_enabled"), rs.getBoolean("post_event_enabled"),
                    rs.getString("review_link"), rs.getBoolean("follow_up_enabled"),
                    rs.getInt("follow_up_days")),
                companyId)
            .stream().findFirst().orElse(EventConfig.defaultFor(companyId));
    }

    public EventConfig upsert(UUID companyId, String businessName, String notes,
                              boolean autoCompleteEnabled, boolean postEventEnabled, String reviewLink,
                              boolean followUpEnabled, int followUpDays) {
        jdbcTemplate.update(
            "insert into event_config (company_id, business_name, notes, auto_complete_enabled, "
                + "post_event_enabled, review_link, follow_up_enabled, follow_up_days) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (company_id) do update set business_name = excluded.business_name, "
                + "notes = excluded.notes, auto_complete_enabled = excluded.auto_complete_enabled, "
                + "post_event_enabled = excluded.post_event_enabled, review_link = excluded.review_link, "
                + "follow_up_enabled = excluded.follow_up_enabled, follow_up_days = excluded.follow_up_days, "
                + "updated_at = now()",
            companyId, businessName, notes, autoCompleteEnabled, postEventEnabled, reviewLink,
            followUpEnabled, followUpDays);
        return findByCompany(companyId);
    }
}
