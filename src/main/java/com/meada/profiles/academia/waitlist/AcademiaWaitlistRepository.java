package com.meada.profiles.academia.waitlist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_class_waitlist} (migration 74). Opera via service_role; escopo por
 * company_id. A POSIÇÃO é sempre DERIVADA no SELECT por um subquery correlacionado (count de
 * 'aguardando' com enqueued_at menor + 1) — nunca persistida.
 */
@Repository
public class AcademiaWaitlistRepository {

    private static final RowMapper<AcademiaWaitlistEntry> MAPPER = (rs, rn) -> new AcademiaWaitlistEntry(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("class_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("student_name"),
        rs.getString("student_phone"),
        rs.getString("status"),
        rs.getTimestamp("enqueued_at").toInstant(),
        rs.getInt("position"));

    // Subquery de posição derivada: quantos 'aguardando' da MESMA aula chegaram antes, +1.
    private static final String POSITION_EXPR =
        "(select count(*) from academia_class_waitlist w2 "
            + " where w2.class_id = w.class_id and w2.status = 'aguardando' "
            + "   and w2.enqueued_at < w.enqueued_at) + 1 as position";

    private static final String COLS =
        "w.id, w.class_id, w.contact_id, w.student_name, w.student_phone, w.status, w.enqueued_at, " + POSITION_EXPR;

    private final JdbcTemplate jdbcTemplate;

    public AcademiaWaitlistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Fila de uma aula, ordenada por chegada (enqueued_at). onlyWaiting → só status 'aguardando'. */
    public List<AcademiaWaitlistEntry> listByClass(UUID companyId, UUID classId, boolean onlyWaiting) {
        String sql = "select " + COLS + " from academia_class_waitlist w "
            + "where w.company_id = ? and w.class_id = ?"
            + (onlyWaiting ? " and w.status = 'aguardando'" : "")
            + " order by w.enqueued_at asc";
        return jdbcTemplate.query(sql, MAPPER, companyId, classId);
    }

    public Optional<AcademiaWaitlistEntry> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from academia_class_waitlist w "
                + "where w.company_id = ? and w.id = ?", MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Enfileira. status nasce 'aguardando'; posição é derivada na leitura. */
    public AcademiaWaitlistEntry insert(UUID companyId, UUID classId, UUID contactId,
                                        String studentName, String studentPhone) {
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_class_waitlist (company_id, class_id, contact_id, student_name, student_phone) "
                + "values (?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, classId, contactId, studentName.trim(), studentPhone);
        return findById(companyId, id).orElseThrow();
    }

    /** Muta o status. Retorna vazio se a entrada não existe / é de outro tenant. */
    public Optional<AcademiaWaitlistEntry> updateStatus(UUID companyId, UUID id, String status) {
        int n = jdbcTemplate.update(
            "update academia_class_waitlist set status = ? where company_id = ? and id = ?",
            status, companyId, id);
        return n == 0 ? Optional.empty() : findById(companyId, id);
    }
}
