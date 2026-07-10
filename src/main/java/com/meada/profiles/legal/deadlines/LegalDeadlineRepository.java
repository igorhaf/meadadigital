package com.meada.profiles.legal.deadlines;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code legal_deadlines} (onda Legal 1). service_role; escopo por company em todo WHERE.
 * As varreduras do lembrete cruzam TODOS os tenants legal (o job roda global) e a idempotência é
 * por (prazo, due_date) em cada janela (D-3/D-1) — remarcar rearma.
 */
@Repository
public class LegalDeadlineRepository {

    private static final RowMapper<LegalDeadline> MAPPER = (rs, rn) -> new LegalDeadline(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("case_id"),
        rs.getString("case_title"),
        rs.getString("kind"),
        rs.getString("title"),
        rs.getDate("due_date").toLocalDate(),
        rs.getObject("due_time", LocalTime.class),
        rs.getString("location"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private static final String SELECT =
        "select d.id, d.case_id, c.title as case_title, d.kind, d.title, d.due_date, d.due_time, "
            + "d.location, d.status, d.notes, d.created_at, d.updated_at "
            + "from legal_deadlines d join legal_cases c on c.id = d.case_id ";

    private final JdbcTemplate jdbcTemplate;

    public LegalDeadlineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LegalDeadline> listByCompany(UUID companyId, String status, UUID caseId,
                                             LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(SELECT + "where d.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and d.status = ?"); args.add(status); }
        if (caseId != null) { sql.append(" and d.case_id = ?"); args.add(caseId); }
        if (from != null) { sql.append(" and d.due_date >= ?"); args.add(Date.valueOf(from)); }
        if (to != null) { sql.append(" and d.due_date <= ?"); args.add(Date.valueOf(to)); }
        sql.append(" order by d.due_date, d.due_time nulls last");
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public Optional<LegalDeadline> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query(SELECT + "where d.company_id = ? and d.id = ?", MAPPER, companyId, id)
            .stream().findFirst();
    }

    public LegalDeadline insert(UUID companyId, UUID caseId, String kind, String title,
                                LocalDate dueDate, LocalTime dueTime, String location, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into legal_deadlines (company_id, case_id, kind, title, due_date, due_time, location, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, caseId, kind, title, Date.valueOf(dueDate),
            dueTime == null ? null : Time.valueOf(dueTime), location, notes);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<LegalDeadline> update(UUID companyId, UUID id, String kind, String title,
                                          LocalDate dueDate, LocalTime dueTime, boolean timeProvided,
                                          String location, String status, String notes) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (kind != null && !kind.isBlank()) { sets.add("kind = ?"); args.add(kind); }
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title); }
        if (dueDate != null) { sets.add("due_date = ?"); args.add(Date.valueOf(dueDate)); }
        if (timeProvided) { sets.add("due_time = ?"); args.add(dueTime == null ? null : Time.valueOf(dueTime)); }
        if (location != null) { sets.add("location = ?"); args.add(location.isBlank() ? null : location); }
        if (status != null && !status.isBlank()) { sets.add("status = ?"); args.add(status); }
        if (notes != null) { sets.add("notes = ?"); args.add(notes.isBlank() ? null : notes); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update("update legal_deadlines set " + String.join(", ", sets)
                + " where company_id = ? and id = ?", args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public boolean delete(UUID companyId, UUID id) {
        return jdbcTemplate.update("delete from legal_deadlines where company_id = ? and id = ?",
            companyId, id) > 0;
    }

    /** Prazos pendentes DUE em {@code targetDate}, não lembrados NESTA janela (col = marker). */
    public List<DueDeadline> findDueForWindow(LocalDate targetDate, String markerColumn) {
        return jdbcTemplate.query(
            "select d.id, d.company_id, d.kind, d.title, d.due_date, d.due_time, d.location, "
                + "c.legal_client_id, c.title as case_title "
                + "from legal_deadlines d "
                + "join legal_cases c on c.id = d.case_id "
                + "join companies co on co.id = d.company_id "
                + "left join legal_config cfg on cfg.company_id = d.company_id "
                + "where co.profile_id = 'legal' "
                + "and coalesce(cfg.deadline_reminder_enabled, true) "
                + "and d.status = 'pendente' "
                + "and d.due_date = ? "
                + "and (d." + markerColumn + " is null or d." + markerColumn + " <> d.due_date) "
                + "order by d.company_id",
            (rs, rn) -> new DueDeadline(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("legal_client_id"),
                rs.getString("kind"),
                rs.getString("title"),
                rs.getDate("due_date").toLocalDate(),
                rs.getObject("due_time", LocalTime.class),
                rs.getString("location"),
                rs.getString("case_title")),
            Date.valueOf(targetDate));
    }

    public void markReminded(UUID deadlineId, LocalDate dueDate, String markerColumn) {
        jdbcTemplate.update(
            "update legal_deadlines set " + markerColumn + " = ?, updated_at = now() where id = ?",
            Date.valueOf(dueDate), deadlineId);
    }
}
