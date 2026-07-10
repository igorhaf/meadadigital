package com.meada.profiles.legal.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Acesso a {@code legal_config} (onda Legal 1). 1:1 com company; ausente → defaults. */
@Repository
public class LegalConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public LegalConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public LegalConfig findByCompany(UUID companyId) {
        return jdbcTemplate.query(
                "select review_link, post_closure_enabled, deadline_reminder_enabled "
                    + "from legal_config where company_id = ?",
                (rs, rn) -> new LegalConfig(companyId, rs.getString("review_link"),
                    rs.getBoolean("post_closure_enabled"), rs.getBoolean("deadline_reminder_enabled")),
                companyId)
            .stream().findFirst().orElse(LegalConfig.defaultFor(companyId));
    }

    public LegalConfig upsert(UUID companyId, String reviewLink, boolean postClosureEnabled,
                              boolean deadlineReminderEnabled) {
        jdbcTemplate.update(
            "insert into legal_config (company_id, review_link, post_closure_enabled, deadline_reminder_enabled) "
                + "values (?, ?, ?, ?) "
                + "on conflict (company_id) do update set review_link = excluded.review_link, "
                + "post_closure_enabled = excluded.post_closure_enabled, "
                + "deadline_reminder_enabled = excluded.deadline_reminder_enabled, updated_at = now()",
            companyId, reviewLink, postClosureEnabled, deadlineReminderEnabled);
        return findByCompany(companyId);
    }
}
