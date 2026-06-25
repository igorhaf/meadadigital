package com.meada.whatsapp.profiles.escola.enrollments;

import com.meada.whatsapp.profiles.escola.classes.EscolaClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code escola_enrollments} (camada 8.19). Opera via service_role.
 *
 * <p>{@link #insertEnrollment} é TRANSACIONAL e faz, DENTRO da transação (defesa race):
 * <ol>
 *   <li>ANTI-DUPLA: rejeita se já existe matrícula ATIVA do MESMO aluno na MESMA turma
 *       ({@link AlreadyActiveException}). O UNIQUE INDEX uniq_active_enrollment_per_student_class é a
 *       defesa final; este check dá a exceção limpa.</li>
 *   <li>CAPACITY POR TURMA: {@code count(matrículas não-canceladas da turma) + 1 <= capacity}; se
 *       estourar, lança {@link ClassFullException} e a transação reverte.</li>
 * </ol>
 * Status inicial = ativa. Snapshots de aluno + turma na própria linha.
 */
@Repository
public class EscolaEnrollmentRepository {

    private static final RowMapper<EscolaEnrollment> MAPPER = (rs, rn) -> new EscolaEnrollment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("class_id"),
        (UUID) rs.getObject("student_id"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("student_name"),
        rs.getString("responsible_name"),
        rs.getString("class_name"),
        rs.getString("class_grade"),
        rs.getString("class_shift"),
        rs.getInt("class_monthly_cents"),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, company_id, class_id, student_id, conversation_id, contact_id, student_name, "
            + "responsible_name, class_name, class_grade, class_shift, class_monthly_cents, start_date, "
            + "end_date, status, notes, created_at, status_updated_at";

    private final JdbcTemplate jdbcTemplate;

    public EscolaEnrollmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EscolaEnrollment> listByCompany(UUID companyId, String status, UUID classId,
                                                UUID studentId, UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from escola_enrollments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (classId != null) { sql.append(" and class_id = ?"); args.add(classId); }
        if (studentId != null) { sql.append(" and student_id = ?"); args.add(studentId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_date desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, UUID classId, UUID studentId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from escola_enrollments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (classId != null) { sql.append(" and class_id = ?"); args.add(classId); }
        if (studentId != null) { sql.append(" and student_id = ?"); args.add(studentId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<EscolaEnrollment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from escola_enrollments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Matrícula ATIVA do MESMO aluno na MESMA turma (se houver) — base da anti-dupla. */
    public Optional<EscolaEnrollment> findActiveByStudentClass(UUID companyId, UUID studentId, UUID classId) {
        return jdbcTemplate.query("select " + COLS + " from escola_enrollments "
                + "where company_id = ? and student_id = ? and class_id = ? and status = 'ativa' limit 1",
                MAPPER, companyId, studentId, classId)
            .stream().findFirst();
    }

    /** Matrículas ATIVAS de um responsável (contact) — pro contexto da IA (qual turma cada filho está). */
    public List<EscolaEnrollment> listActiveByContact(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return List.of();
        }
        return jdbcTemplate.query("select " + COLS + " from escola_enrollments "
                + "where company_id = ? and contact_id = ? and status = 'ativa' order by start_date desc",
            MAPPER, companyId, contactId);
    }

    /** Conta as matrículas não-canceladas (ativa+suspensa) que ocupam vaga numa turma. */
    public int countActiveByClass(UUID classId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from escola_enrollments where class_id = ? and status <> 'cancelada'",
            Integer.class, classId);
        return n == null ? 0 : n;
    }

    /**
     * Cria a matrícula numa transação. Anti-dupla (1 ativa por aluno+turma) + capacity por turma são
     * validadas DENTRO da transação antes do INSERT (defesa race); a violação reverte tudo. Snapshots
     * de aluno (studentName/responsibleName) e turma (className/grade/shift/monthlyCents). Status
     * inicial = ativa.
     */
    @Transactional
    public EscolaEnrollment insertEnrollment(UUID companyId, EscolaClass clazz, UUID studentId,
                                             String studentName, String responsibleName,
                                             UUID conversationId, UUID contactId, String notes) {
        // anti-dupla: 1 matrícula ATIVA do mesmo aluno na mesma turma.
        if (findActiveByStudentClass(companyId, studentId, clazz.id()).isPresent()) {
            throw new AlreadyActiveException();
        }
        // capacity por turma (ativa+suspensa ocupam; cancelada libera).
        int current = countActiveByClass(clazz.id());
        if (current + 1 > clazz.capacity()) {
            throw new ClassFullException(clazz.id(), clazz.name());
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into escola_enrollments (company_id, class_id, student_id, conversation_id, contact_id, "
                + "student_name, responsible_name, class_name, class_grade, class_shift, class_monthly_cents, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, clazz.id(), studentId, conversationId, contactId,
            studentName, responsibleName, clazz.name(), clazz.grade(), clazz.shift(),
            clazz.monthlyCents(), notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição + status_updated_at. Em cancelada, materializa end_date = hoje. */
    public void updateStatus(UUID companyId, UUID id, String newStatus, LocalDate endDate) {
        if (endDate != null) {
            jdbcTemplate.update("update escola_enrollments set status = ?, end_date = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?", newStatus, Date.valueOf(endDate), companyId, id);
        } else {
            jdbcTemplate.update("update escola_enrollments set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?", newStatus, companyId, id);
        }
    }

    /** Lançada quando já existe matrícula ativa do mesmo aluno na mesma turma. */
    public static class AlreadyActiveException extends RuntimeException {}

    /** Lançada pelo insert quando a turma não tem vaga. Carrega classId + className. */
    public static class ClassFullException extends RuntimeException {
        private final UUID classId;
        private final String className;

        public ClassFullException(UUID classId, String className) {
            this.classId = classId;
            this.className = className;
        }

        public UUID classId() {
            return classId;
        }

        public String className() {
            return className;
        }
    }
}
