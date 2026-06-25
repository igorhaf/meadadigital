package com.meada.whatsapp.profiles.escola.visits;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code escola_visits} (camada 8.19, ESCAPADA 2). Opera via service_role; escopo por
 * company_id. Agenda LEVE: dia + período, SEM conflito de capacidade.
 */
@Repository
public class EscolaVisitRepository {

    private static final RowMapper<EscolaVisit> MAPPER = (rs, rn) -> new EscolaVisit(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        (UUID) rs.getObject("student_id"),
        rs.getString("visitor_name"),
        rs.getString("visitor_phone"),
        rs.getObject("visit_date", LocalDate.class),
        rs.getString("period"),
        (Integer) rs.getObject("num_people"),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, company_id, conversation_id, contact_id, student_id, visitor_name, visitor_phone, "
            + "visit_date, period, num_people, status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public EscolaVisitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EscolaVisit> listByCompany(UUID companyId, String status, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from escola_visits where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        sql.append(" order by visit_date desc, created_at desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status) {
        StringBuilder sql = new StringBuilder("select count(*) from escola_visits where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<EscolaVisit> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from escola_visits where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    public EscolaVisit insert(UUID companyId, UUID conversationId, UUID contactId, UUID studentId,
                              String visitorName, String visitorPhone, LocalDate visitDate, String period,
                              Integer numPeople, String notes) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into escola_visits (company_id, conversation_id, contact_id, student_id, visitor_name, "
                + "visitor_phone, visit_date, period, num_people, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, conversationId, contactId, studentId, visitorName, visitorPhone,
            Date.valueOf(visitDate), period, numPeople, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição + status_updated_at. */
    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update("update escola_visits set status = ?, status_updated_at = now() "
            + "where company_id = ? and id = ?", newStatus, companyId, id);
    }
}
