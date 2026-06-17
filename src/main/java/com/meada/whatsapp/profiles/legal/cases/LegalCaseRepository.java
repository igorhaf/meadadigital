package com.meada.whatsapp.profiles.legal.cases;

import com.meada.whatsapp.profiles.legal.LegalCnjValidator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code legal_cases} + {@code legal_case_updates} (camada 7.2). Opera via service_role.
 */
@Repository
public class LegalCaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public LegalCaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<LegalCaseUpdate> UPDATE_MAPPER = (rs, rn) -> new LegalCaseUpdate(
        (UUID) rs.getObject("id"),
        rs.getString("title"),
        rs.getString("body"),
        rs.getTimestamp("occurred_at").toInstant(),
        rs.getTimestamp("created_at").toInstant());

    /** Mapeia a row de case (join com client p/ o nome + count de andamentos). updates à parte. */
    private LegalCase mapCase(java.sql.ResultSet rs, List<LegalCaseUpdate> updates) throws java.sql.SQLException {
        String cnj = rs.getString("cnj_number");
        return new LegalCase(
            (UUID) rs.getObject("id"),
            (UUID) rs.getObject("legal_client_id"),
            rs.getString("client_name"),
            cnj,
            LegalCnjValidator.format(cnj),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("court"),
            rs.getString("forum"),
            rs.getString("subject"),
            rs.getString("status"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("status_updated_at").toInstant(),
            rs.getInt("updates_count"),
            updates);
    }

    private static final String CASE_SELECT =
        "select c.id, c.legal_client_id, lc.name as client_name, c.cnj_number, c.title, "
            + "c.description, c.court, c.forum, c.subject, c.status, c.created_at, c.updated_at, "
            + "c.status_updated_at, "
            + "(select count(*) from legal_case_updates u where u.legal_case_id = c.id) as updates_count "
            + "from legal_cases c join legal_clients lc on lc.id = c.legal_client_id ";

    public List<LegalCase> listByCompany(UUID companyId, String status, String search,
                                         int limit, int offset) {
        StringBuilder sql = new StringBuilder(CASE_SELECT + "where c.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) {
            sql.append(" and c.status = ?");
            args.add(status);
        }
        if (search != null && !search.isBlank()) {
            sql.append(" and (c.title ilike ? or c.cnj_number ilike ? or lc.name ilike ?)");
            String like = "%" + search.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        sql.append(" order by c.updated_at desc limit ? offset ?");
        args.add(limit); args.add(offset);
        return jdbcTemplate.query(sql.toString(), (rs, rn) -> mapCase(rs, List.of()), args.toArray());
    }

    public long countByCompany(UUID companyId, String status, String search) {
        StringBuilder sql = new StringBuilder(
            "select count(*) from legal_cases c join legal_clients lc on lc.id = c.legal_client_id "
                + "where c.company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and c.status = ?"); args.add(status); }
        if (search != null && !search.isBlank()) {
            sql.append(" and (c.title ilike ? or c.cnj_number ilike ? or lc.name ilike ?)");
            String like = "%" + search.trim() + "%";
            args.add(like); args.add(like); args.add(like);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public List<LegalCase> listByLegalClient(UUID companyId, UUID legalClientId) {
        return jdbcTemplate.query(CASE_SELECT + "where c.company_id = ? and c.legal_client_id = ? "
                + "order by c.updated_at desc",
            (rs, rn) -> mapCase(rs, List.of()), companyId, legalClientId);
    }

    /** Detalhe com os andamentos (occurred_at desc, até 50). */
    public Optional<LegalCase> findById(UUID companyId, UUID id) {
        Optional<LegalCase> base = jdbcTemplate.query(CASE_SELECT + "where c.company_id = ? and c.id = ?",
                (rs, rn) -> mapCase(rs, List.of()), companyId, id)
            .stream().findFirst();
        return base.map(c -> {
            List<LegalCaseUpdate> updates = listUpdatesByCase(id, 50, 0);
            return new LegalCase(c.id(), c.legalClientId(), c.legalClientName(), c.cnjNumber(),
                c.cnjNumberFormatted(), c.title(), c.description(), c.court(), c.forum(), c.subject(),
                c.status(), c.createdAt(), c.updatedAt(), c.statusUpdatedAt(), c.updatesCount(), updates);
        });
    }

    /** Insere; a violação do UNIQUE (company_id, cnj_number) propaga DuplicateKeyException. */
    public LegalCase insert(UUID companyId, UUID legalClientId, String cnjNumber, String title,
                            String description, String court, String forum, String subject) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into legal_cases (company_id, legal_client_id, cnj_number, title, description, "
                + "court, forum, subject) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, legalClientId, cnjNumber, title.trim(), description, court, forum, subject);
        return findById(companyId, id).orElseThrow();
    }

    public Optional<LegalCase> update(UUID companyId, UUID id, String title, String description,
                                      String court, String forum, String subject) {
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (title != null && !title.isBlank()) { sets.add("title = ?"); args.add(title.trim()); }
        if (description != null) { sets.add("description = ?"); args.add(description); }
        if (court != null) { sets.add("court = ?"); args.add(court); }
        if (forum != null) { sets.add("forum = ?"); args.add(forum); }
        if (subject != null) { sets.add("subject = ?"); args.add(subject); }
        if (!sets.isEmpty()) {
            sets.add("updated_at = now()");
            args.add(companyId);
            args.add(id);
            int n = jdbcTemplate.update(
                "update legal_cases set " + String.join(", ", sets) + " where company_id = ? and id = ?",
                args.toArray());
            if (n == 0) {
                return Optional.empty();
            }
        }
        return findById(companyId, id);
    }

    public void updateStatus(UUID companyId, UUID id, String newStatus) {
        jdbcTemplate.update(
            "update legal_cases set status = ?, status_updated_at = now(), updated_at = now() "
                + "where company_id = ? and id = ?",
            newStatus, companyId, id);
    }

    public List<LegalCaseUpdate> listUpdatesByCase(UUID legalCaseId, int limit, int offset) {
        return jdbcTemplate.query(
            "select id, title, body, occurred_at, created_at from legal_case_updates "
                + "where legal_case_id = ? order by occurred_at desc limit ? offset ?",
            UPDATE_MAPPER, legalCaseId, limit, offset);
    }

    public LegalCaseUpdate insertUpdate(UUID legalCaseId, String title, String body,
                                        java.time.Instant occurredAt) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into legal_case_updates (legal_case_id, title, body, occurred_at) "
                + "values (?, ?, ?, ?) returning id",
            UUID.class, legalCaseId, title.trim(), body, java.sql.Timestamp.from(occurredAt));
        return jdbcTemplate.queryForObject(
            "select id, title, body, occurred_at, created_at from legal_case_updates where id = ?",
            UPDATE_MAPPER, id);
    }

    public boolean deleteUpdate(UUID legalCaseId, UUID updateId) {
        return jdbcTemplate.update(
            "delete from legal_case_updates where legal_case_id = ? and id = ?",
            legalCaseId, updateId) > 0;
    }

    /** legal_client_id de um processo (para resolver o contato na notificação/cache). */
    public Optional<UUID> findClientId(UUID companyId, UUID caseId) {
        return jdbcTemplate.query(
                "select legal_client_id from legal_cases where company_id = ? and id = ?",
                (rs, rn) -> (UUID) rs.getObject("legal_client_id"), companyId, caseId)
            .stream().findFirst();
    }

    public Optional<String> findStatus(UUID companyId, UUID caseId) {
        return jdbcTemplate.query(
                "select status from legal_cases where company_id = ? and id = ?",
                (rs, rn) -> rs.getString("status"), companyId, caseId)
            .stream().findFirst();
    }
}
