package com.meada.profiles.escola.waitlist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lista de espera por turma (onda Escola 1, backlog #1). Enfileira o lead quando a matrícula
 * bate em class_full; posição DERIVADA por count (espelho da fila do barbearia — sem coluna
 * position). Avisar o 1º da fila quando abre vaga é AÇÃO HUMANA no painel — a IA nunca promete
 * vaga (trava intacta). Best-effort.
 */
@Service
public class EscolaWaitlistService {

    private static final Logger log = LoggerFactory.getLogger(EscolaWaitlistService.class);

    private final JdbcTemplate jdbcTemplate;
    private final com.meada.profiles.escola.visits.EscolaVisitNotifier notifier;

    public EscolaWaitlistService(JdbcTemplate jdbcTemplate,
                                 com.meada.profiles.escola.visits.EscolaVisitNotifier notifier) {
        this.jdbcTemplate = jdbcTemplate;
        this.notifier = notifier;
    }

    /**
     * Enfileira o interesse (chamado pelo handler no class_full). O nome do aluno vem do cadastro
     * (studentId nullable quando o new_student nem chegou a ser criado — snapshot pelo contato).
     * Duplicata pendente é no-op (unique parcial).
     */
    public boolean enqueue(UUID companyId, UUID classId, UUID contactId, UUID studentId) {
        String studentName = null;
        if (studentId != null) {
            studentName = jdbcTemplate.query(
                    "select name from escola_students where company_id = ? and id = ?",
                    (rs, rn) -> rs.getString("name"), companyId, studentId)
                .stream().findFirst().orElse(null);
        }
        if (studentName == null) {
            studentName = jdbcTemplate.query(
                    "select name from contacts where company_id = ? and id = ?",
                    (rs, rn) -> rs.getString("name"), companyId, contactId)
                .stream().findFirst().orElse("(aluno)");
        }
        try {
            int n = jdbcTemplate.update(
                "insert into escola_waitlist (company_id, class_id, contact_id, student_id, student_name) "
                    + "values (?, ?, ?, ?, ?) on conflict do nothing",
                companyId, classId, contactId, studentId, studentName);
            return n > 0;
        } catch (Exception e) {
            log.warn("escola-waitlist: enqueue falhou p/ turma {} ({})", classId, e.getMessage());
            return false;
        }
    }

    /** Fila de uma turma (aguardando, mais antigos primeiro) com posição derivada. */
    public List<Map<String, Object>> listByClass(UUID companyId, UUID classId) {
        return jdbcTemplate.query(
            "select w.id, w.student_name, w.status, w.created_at, w.notified_at, "
                + "ct.name as contact_name, ct.phone_number as contact_phone, "
                + "(select count(*) from escola_waitlist p "
                + "  where p.class_id = w.class_id and p.status = 'aguardando' "
                + "  and p.created_at < w.created_at) + 1 as position "
                + "from escola_waitlist w join contacts ct on ct.id = w.contact_id "
                + "where w.company_id = ? and w.class_id = ? and w.status in ('aguardando','avisada') "
                + "order by w.created_at",
            (rs, rn) -> Map.of(
                "id", rs.getObject("id"),
                "studentName", rs.getString("student_name"),
                "status", rs.getString("status"),
                "contactName", rs.getString("contact_name") == null ? "" : rs.getString("contact_name"),
                "contactPhone", rs.getString("contact_phone") == null ? "" : rs.getString("contact_phone"),
                "position", rs.getLong("position"),
                "createdAt", rs.getTimestamp("created_at").toInstant().toString()),
            companyId, classId);
    }

    /** Quantos aguardando por turma (badge do painel). */
    public long countPending(UUID companyId, UUID classId) {
        Long n = jdbcTemplate.queryForObject(
            "select count(*) from escola_waitlist where company_id = ? and class_id = ? and status = 'aguardando'",
            Long.class, companyId, classId);
        return n == null ? 0 : n;
    }

    /**
     * AÇÃO HUMANA do painel: avisa a família que abriu vaga (via conversa mais recente do contato)
     * e marca 'avisada'. A matrícula em si continua vindo pela conversa (gate normal).
     */
    public Optional<UUID> notifyOpening(UUID companyId, UUID waitlistId) {
        record Entry(UUID contactId, String studentName, UUID classId, String className) {}
        Entry e = jdbcTemplate.query(
                "select w.contact_id, w.student_name, w.class_id, c.name as class_name "
                    + "from escola_waitlist w join escola_classes c on c.id = w.class_id "
                    + "where w.company_id = ? and w.id = ? and w.status = 'aguardando'",
                (rs, rn) -> new Entry((UUID) rs.getObject("contact_id"), rs.getString("student_name"),
                    (UUID) rs.getObject("class_id"), rs.getString("class_name")),
                companyId, waitlistId)
            .stream().findFirst().orElse(null);
        if (e == null) {
            return Optional.empty();
        }
        UUID conversationId = jdbcTemplate.query(
                "select id from conversations where company_id = ? and contact_id = ? "
                    + "order by created_at desc limit 1",
                (rs, rn) -> (UUID) rs.getObject("id"), companyId, e.contactId())
            .stream().findFirst().orElse(null);
        if (conversationId != null) {
            notifier.notifyStatus(companyId, conversationId,
                "Boa notícia! Abriu uma vaga na turma " + e.className() + " que você esperava para "
                    + e.studentName() + ". Quer garantir a matrícula? É só responder por aqui que a "
                    + "secretaria confirma os detalhes. 🎒");
        }
        jdbcTemplate.update(
            "update escola_waitlist set status = 'avisada', notified_at = now(), status_updated_at = now() "
                + "where id = ?", waitlistId);
        return Optional.of(waitlistId);
    }

    /** Gestão da secretaria: convertida (virou matrícula) ou desistiu. */
    public boolean updateStatus(UUID companyId, UUID waitlistId, String status) {
        return jdbcTemplate.update(
            "update escola_waitlist set status = ?, status_updated_at = now() "
                + "where company_id = ? and id = ? and status in ('aguardando','avisada')",
            status, companyId, waitlistId) > 0;
    }
}
