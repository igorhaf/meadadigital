package com.meada.whatsapp.profiles.academia.memberships;

import com.meada.whatsapp.profiles.academia.classes.AcademiaClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acesso a {@code academia_memberships} + junction {@code academia_membership_classes} (camada 7.7).
 * Opera via service_role.
 *
 * <p>{@link #insertMembership} é TRANSACIONAL e faz a validação de VAGA POR AULA dentro da transação
 * (defesa race): para cada aula, {@code count(matrículas não-canceladas) + 1 <= capacity}. Se alguma
 * estoura, lança {@link ClassFullException(classId, className)} e a transação reverte. Snapshots de
 * plano + student na matrícula; snapshots da aula na junction.
 */
@Repository
public class AcademiaMembershipRepository {

    private static final RowMapper<AcademiaMembership> HEAD_MAPPER = (rs, rn) -> new AcademiaMembership(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("plan_id"),
        rs.getString("plan_name"),
        rs.getInt("plan_monthly_cents"),
        (UUID) rs.getObject("conversation_id"),
        (UUID) rs.getObject("contact_id"),
        rs.getString("student_name"),
        rs.getString("student_phone"),
        rs.getObject("start_date", LocalDate.class),
        rs.getObject("end_date", LocalDate.class),
        rs.getString("status"),
        rs.getString("notes"),
        List.of(),   // classes hidratadas à parte
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("status_updated_at").toInstant());

    private static final String HEAD_COLS =
        "id, plan_id, plan_name, plan_monthly_cents, conversation_id, contact_id, student_name, "
            + "student_phone, start_date, end_date, status, notes, created_at, status_updated_at";

    private static final RowMapper<MembershipClassEntry> ENTRY_MAPPER = (rs, rn) -> new MembershipClassEntry(
        (UUID) rs.getObject("class_id"),
        rs.getString("class_name_snapshot"),
        rs.getInt("class_day_of_week_snapshot"),
        rs.getObject("class_start_time_snapshot", LocalTime.class),
        rs.getInt("class_duration_minutes_snapshot"),
        rs.getString("class_modality_snapshot"));

    private final JdbcTemplate jdbcTemplate;

    public AcademiaMembershipRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private List<MembershipClassEntry> loadClasses(UUID membershipId) {
        return jdbcTemplate.query(
            "select class_id, class_name_snapshot, class_day_of_week_snapshot, class_start_time_snapshot, "
                + "class_duration_minutes_snapshot, class_modality_snapshot "
                + "from academia_membership_classes where membership_id = ? order by class_day_of_week_snapshot, class_start_time_snapshot",
            ENTRY_MAPPER, membershipId);
    }

    private AcademiaMembership withClasses(AcademiaMembership m) {
        return new AcademiaMembership(m.id(), m.planId(), m.planName(), m.planMonthlyCents(),
            m.conversationId(), m.contactId(), m.studentName(), m.studentPhone(), m.startDate(),
            m.endDate(), m.status(), m.notes(), loadClasses(m.id()), m.createdAt(), m.statusUpdatedAt());
    }

    public List<AcademiaMembership> listByCompany(UUID companyId, String status, UUID planId,
                                                  UUID classId, UUID contactId, int limit, int offset) {
        StringBuilder sql = new StringBuilder("select " + HEAD_COLS + " from academia_memberships m where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (planId != null) { sql.append(" and plan_id = ?"); args.add(planId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (classId != null) {
            sql.append(" and exists (select 1 from academia_membership_classes mc where mc.membership_id = m.id and mc.class_id = ?)");
            args.add(classId);
        }
        sql.append(" order by start_date desc limit ? offset ?");
        args.add(limit);
        args.add(offset);
        List<AcademiaMembership> heads = jdbcTemplate.query(sql.toString(), HEAD_MAPPER, args.toArray());
        List<AcademiaMembership> out = new ArrayList<>(heads.size());
        for (AcademiaMembership h : heads) {
            out.add(withClasses(h));
        }
        return out;
    }

    public long countByCompany(UUID companyId, String status, UUID planId, UUID classId, UUID contactId) {
        StringBuilder sql = new StringBuilder("select count(*) from academia_memberships m where company_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(companyId);
        if (status != null && !status.isBlank()) { sql.append(" and status = ?"); args.add(status); }
        if (planId != null) { sql.append(" and plan_id = ?"); args.add(planId); }
        if (contactId != null) { sql.append(" and contact_id = ?"); args.add(contactId); }
        if (classId != null) {
            sql.append(" and exists (select 1 from academia_membership_classes mc where mc.membership_id = m.id and mc.class_id = ?)");
            args.add(classId);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0L : n;
    }

    public Optional<AcademiaMembership> findById(UUID companyId, UUID id) {
        return jdbcTemplate.query("select " + HEAD_COLS + " from academia_memberships where company_id = ? and id = ?",
                HEAD_MAPPER, companyId, id)
            .stream().findFirst().map(this::withClasses);
    }

    /** Matrícula ATIVA do contato (se houver) — pra IA/service detectar dupla. */
    public Optional<AcademiaMembership> findActiveByContact(UUID companyId, UUID contactId) {
        if (contactId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("select " + HEAD_COLS + " from academia_memberships "
                + "where company_id = ? and contact_id = ? and status = 'ativa' limit 1",
                HEAD_MAPPER, companyId, contactId)
            .stream().findFirst().map(this::withClasses);
    }

    /** Conta matrículas não-canceladas (ativa+suspensa) que ocupam vaga numa aula. */
    public int countActiveByClass(UUID classId) {
        Integer n = jdbcTemplate.queryForObject(
            "select count(*) from academia_membership_classes mc "
                + "join academia_memberships m on m.id = mc.membership_id "
                + "where mc.class_id = ? and m.status <> 'cancelada'",
            Integer.class, classId);
        return n == null ? 0 : n;
    }

    /**
     * Cria a matrícula + junction numa transação. Para cada aula pedida, valida {@code
     * countActiveByClass + 1 <= capacity} (vaga); se estourar, lança {@link ClassFullException} e a
     * transação reverte. Snapshots de plano (na matrícula) e de aula (na junction). Status inicial
     * = ativa. O UNIQUE INDEX uniq_active_membership_per_contact é a defesa final contra dupla.
     */
    @Transactional
    public AcademiaMembership insertMembership(UUID companyId, UUID planId, String planName,
                                               int planMonthlyCents, UUID conversationId, UUID contactId,
                                               String studentName, String studentPhone, String notes,
                                               List<AcademiaClass> classes) {
        // valida vaga por aula DENTRO da transação.
        for (AcademiaClass c : classes) {
            int current = countActiveByClass(c.id());
            if (current + 1 > c.capacity()) {
                throw new ClassFullException(c.id(), c.name());
            }
        }
        UUID id = jdbcTemplate.queryForObject(
            "insert into academia_memberships (company_id, plan_id, conversation_id, contact_id, "
                + "student_name, student_phone, plan_name, plan_monthly_cents, notes) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            UUID.class, companyId, planId, conversationId, contactId, studentName, studentPhone,
            planName, planMonthlyCents, notes);
        for (AcademiaClass c : classes) {
            jdbcTemplate.update(
                "insert into academia_membership_classes (membership_id, class_id, class_name_snapshot, "
                    + "class_day_of_week_snapshot, class_start_time_snapshot, class_duration_minutes_snapshot, "
                    + "class_modality_snapshot) values (?, ?, ?, ?, ?, ?, ?)",
                id, c.id(), c.name(), c.dayOfWeek(), Time.valueOf(c.startTime()), c.durationMinutes(), c.modality());
        }
        return findById(companyId, id).orElseThrow();
    }

    /** Persiste a transição + status_updated_at. Em cancelada, materializa end_date = hoje. */
    public void updateStatus(UUID companyId, UUID id, String newStatus, LocalDate endDate) {
        if (endDate != null) {
            jdbcTemplate.update("update academia_memberships set status = ?, end_date = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?", newStatus, Date.valueOf(endDate), companyId, id);
        } else {
            jdbcTemplate.update("update academia_memberships set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ?", newStatus, companyId, id);
        }
    }

    /** Lançada pelo insert quando uma aula pedida não tem vaga. Carrega classId + className. */
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
