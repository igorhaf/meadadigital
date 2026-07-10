package com.meada.profiles.cursos.enrollments;

import com.meada.profiles.cursos.modules.CursosModule;
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
 * Acesso a {@code cursos_enrollments} + progresso {@code cursos_enrollment_progress} (camada 8.20 /
 * perfil cursos). Opera via service_role; escopo por company_id. Clone do AcademiaMembershipRepository
 * (camada 7.7) com 2 diferenças: (1) NÃO há junction de aulas (a matrícula é num curso só); (2) há o
 * PROGRESSO por módulo (ESCAPADA 2): {@link #findNextModule}, {@link #recordProgress},
 * {@link #progressSummary}.
 *
 * <p>{@link #insertEnrollment} é TRANSACIONAL e re-checa o anti-dupla DENTRO da transação (defesa
 * race; o UNIQUE INDEX parcial uniq_active_enrollment_per_contact_course é a defesa final). SEM check
 * de capacity (curso tem vagas ilimitadas nesta SM).
 */
@Repository
public class CursosEnrollmentRepository {

    private static final RowMapper<CursosEnrollment> MAPPER = (rs, rn) -> new CursosEnrollment(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("course_id"),
        rs.getString("course_title"),
        rs.getInt("course_monthly_cents"),
        rs.getInt("discount_cents"),
        rs.getString("coupon_code_snapshot"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("student_name"),
        rs.getString("student_phone"),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        rs.getString("status"),
        rs.getString("notes"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String COLS =
        "id, course_id, course_title, course_monthly_cents, discount_cents, coupon_code_snapshot, "
            + "conversation_id, contact_id, student_name, "
            + "student_phone, start_date, end_date, status, notes, created_at, status_updated_at";

    private static final RowMapper<CursosModule> MODULE_MAPPER = (rs, rn) -> new CursosModule(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("course_id"),
        rs.getInt("position"),
        rs.getString("title"),
        rs.getString("content"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public CursosEnrollmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CursosEnrollment> listByCompany(UUID companyId, String status, UUID courseId,
                                                UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + COLS + " from cursos_enrollments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (courseId != null) { sql.append(" and course_id = ?"); args.add(courseId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        sql.append(" order by start_date desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), MAPPER, args.toArray());
    }

    public long countByCompany(UUID companyId, String status, UUID courseId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from cursos_enrollments where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (courseId != null) { sql.append(" and course_id = ?"); args.add(courseId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<CursosEnrollment> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + COLS + " from cursos_enrollments where company_id = ? and id = ?",
                MAPPER, companyId, id)
            .stream().findFirst();
    }

    /** Matrícula ATIVA do contato NESSE curso (se houver) — pra service detectar dupla (anti-dupla). */
    public Optional<CursosEnrollment> findActiveByContactAndCourse(UUID companyId, UUID contactId, UUID courseId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select " + COLS + " from cursos_enrollments "
                + "where company_id = ? and contact_id = ? and course_id = ? and status = 'ativa' limit 1",
                MAPPER, companyId, contactId, courseId)
            .stream().findFirst();
    }

    /**
     * Cria a matrícula numa transação. Re-checa o anti-dupla (matrícula ativa do contato no mesmo
     * curso) DENTRO da transação; se houver, lança {@link AlreadyEnrolledException}. Snapshots de curso
     * + student. Status inicial = ativa. O UNIQUE INDEX parcial é a defesa final contra dupla.
     */
    @Transactional
    public CursosEnrollment insertEnrollment(UUID companyId, UUID courseId, String courseTitle,
                                             int courseMonthlyCents, int discountCents, String couponCode,
                                             UUID conversationId, UUID contactId,
                                             String studentName, String studentPhone, String notes) {
        if (contactId != null && findActiveByContactAndCourse(companyId, contactId, courseId).isPresent()) {
            throw new AlreadyEnrolledException();
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into cursos_enrollments (company_id, course_id, conversation_id, contact_id, "
                + "student_name, student_phone, course_title, course_monthly_cents, "
                + "discount_cents, coupon_code_snapshot, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, courseId, conversationId, contactId, studentName, studentPhone,
            courseTitle, courseMonthlyCents, discountCents, couponCode, notes);
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição + status_updated_at. Em concluida/cancelada, materializa end_date. */
    public void updateStatus(UUID companyId, UUID id, String newStatus, LocalDate endDate) {
        if (endDate != null) {
            jdbcTemplate.update("update cursos_enrollments set status = ?, end_date = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?", newStatus, Date.valueOf(endDate), companyId, id);
        } else {
            jdbcTemplate.update("update cursos_enrollments set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?", newStatus, companyId, id);
        }
    }

    // --- PROGRESSO (ESCAPADA 2) ---

    /**
     * O "próximo módulo" da matrícula = o 1º módulo (por position ASC) do curso da matrícula cujo
     * module_id NÃO está em cursos_enrollment_progress dessa matrícula. Vazio = trilha concluída.
     */
    public Optional<CursosModule> findNextModule(UUID enrollmentId) {
        return jdbcTemplate.query(
            "select m.id, m.course_id, m.position, m.title, m.content, m.created_at, m.updated_at "
                + "from cursos_modules m "
                + "join cursos_enrollments e on e.course_id = m.course_id "
                + "where e.id = ? and not exists ("
                + "  select 1 from cursos_enrollment_progress p "
                + "  where p.enrollment_id = e.id and p.module_id = m.id) "
                + "order by m.position asc limit 1",
            MODULE_MAPPER, enrollmentId).stream().findFirst();
    }

    /** Registra a conclusão de um módulo pela matrícula (idempotente — ON CONFLICT DO NOTHING). */
    public void recordProgress(UUID enrollmentId, UUID moduleId) {
        jdbcTemplate.update(
            "insert into cursos_enrollment_progress (enrollment_id, module_id) values (?, ?) "
                + "on conflict (enrollment_id, module_id) do nothing",
            enrollmentId, moduleId);
    }

    /** Quantos módulos a matrícula já concluiu. */
    public int doneCount(UUID enrollmentId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from cursos_enrollment_progress where enrollment_id = ?",
            Integer.class, enrollmentId);
        return n == null ? 0 : n;
    }

    /** Total de módulos do curso da matrícula. */
    public int totalModules(UUID enrollmentId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from cursos_modules m "
                + "join cursos_enrollments e on e.course_id = m.course_id where e.id = ?",
            Integer.class, enrollmentId);
        return n == null ? 0 : n;
    }
}
