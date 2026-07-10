package com.meada.profiles.cursos.enrollments;

import com.meada.profiles.cursos.CursoEnrollmentStatus;
import com.meada.profiles.cursos.CursosContextCache;
import com.meada.profiles.cursos.courses.CursosCourse;
import com.meada.profiles.cursos.courses.CursosCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das matrículas do tenant cursos (camada 8.20 / perfil cursos). Clone do
 * AcademiaMembershipService (camada 7.7), mas a matrícula é num ÚNICO curso (sem aulas/vaga).
 *
 * <p>{@link #create} valida o curso (ativo) e o anti-dupla (matrícula ativa do contato NO MESMO
 * curso); delega ao repo (que re-checa o anti-dupla dentro da transação). Status inicial = ativa;
 * notifica boas-vindas. {@link #updateStatus} valida a transição; em concluida/cancelada materializa
 * end_date; notifica ativa/concluida/cancelada (trancada silenciosa).
 */
@Service
public class CursosEnrollmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final CursosEnrollmentRepository enrollmentRepository;
    private final CursosCourseRepository courseRepository;
    private final CursosEnrollmentNotifier notifier;
    private final com.meada.profiles.cursos.coupons.CursosCouponRepository couponRepository;
    private final com.meada.profiles.cursos.certificates.CursosCertificateService certificateService;
    private final com.meada.profiles.cursos.config.CursosConfigRepository configRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final CursosContextCache contextCache;

    public CursosEnrollmentService(CursosEnrollmentRepository enrollmentRepository,
                                   CursosCourseRepository courseRepository,
                                   CursosEnrollmentNotifier notifier,
                                   CursosContextCache contextCache,
                                   com.meada.profiles.cursos.coupons.CursosCouponRepository couponRepository,
                                   com.meada.profiles.cursos.certificates.CursosCertificateService certificateService,
                                   com.meada.profiles.cursos.config.CursosConfigRepository configRepository,
                                   org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.notifier = notifier;
        this.couponRepository = couponRepository;
        this.certificateService = certificateService;
        this.configRepository = configRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.contextCache = contextCache;
    }

    public static class EnrollmentNotFoundException extends RuntimeException {}
    public static class CourseNotFoundException extends RuntimeException {}
    public static class CourseInactiveException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /**
     * Cria a matrícula. Valida curso ativo + anti-dupla; delega ao repo (anti-dupla transacional).
     * Status ativa + notificação de boas-vindas.
     */
    @Transactional
    public CursosEnrollment create(UUID companyId, UUID courseId, UUID contactId, UUID conversationId,
                                   String studentName, String studentPhone, String couponCode, String notes) {
        CursosCourse course = courseRepository.findById(companyId, courseId).orElseThrow(CourseNotFoundException::new);
        if (!course.active()) {
            throw new CourseInactiveException();
        }
        if (contactId != null
            && enrollmentRepository.findActiveByContactAndCourse(companyId, contactId, courseId).isPresent()) {
            throw new AlreadyEnrolledException();
        }
        // Onda 1 (backlog #3): cupom validado no backend — inválido NÃO aborta (sem desconto).
        int discount = 0;
        String couponSnapshot = null;
        if (couponCode != null && !couponCode.isBlank()) {
            var maybe = couponRepository.findByCode(companyId, couponCode);
            if (maybe.isPresent()) {
                var c = maybe.get();
                boolean valid = c.active()
                    && (c.validUntil() == null || !c.validUntil().isBefore(java.time.LocalDate.now()))
                    && (c.maxUses() == null || c.uses() < c.maxUses())
                    && course.monthlyCents() >= c.minOrderCents();
                if (valid) {
                    int raw = "percent".equals(c.kind())
                        ? course.monthlyCents() * c.value() / 100 : c.value();
                    discount = Math.min(course.monthlyCents(), raw);
                    couponSnapshot = c.code();
                    couponRepository.incrementUses(companyId, c.id());
                }
            }
        }
        CursosEnrollment created = enrollmentRepository.insertEnrollment(companyId, courseId, course.title(),
            course.monthlyCents(), discount, couponSnapshot, conversationId, contactId, studentName,
            studentPhone, notes);
        // boas-vindas (status inicial ativa).
        notifier.notifyStatus(companyId, conversationId,
            CursoEnrollmentStatus.ATIVA.notificationText(created.studentName(), created.courseTitle()));
        contextCache.invalidate(companyId);
        return created;
    }

    public List<CursosEnrollment> list(UUID companyId, String status, UUID courseId, UUID contactId,
                                       int limit, int offset) {
        return enrollmentRepository.listByCompany(companyId, status, courseId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, UUID courseId, UUID contactId) {
        return enrollmentRepository.countByCompany(companyId, status, courseId, contactId);
    }

    public Optional<CursosEnrollment> get(UUID companyId, UUID id) {
        return enrollmentRepository.findById(companyId, id);
    }

    /** Resumo de progresso de uma matrícula: módulos concluídos + total + próximo título. */
    public record ProgressSummary(int doneCount, int totalModules, String nextModuleTitle) {}

    public ProgressSummary progress(UUID enrollmentId) {
        int done = enrollmentRepository.doneCount(enrollmentId);
        int total = enrollmentRepository.totalModules(enrollmentId);
        String next = enrollmentRepository.findNextModule(enrollmentId).map(m -> m.title()).orElse(null);
        return new ProgressSummary(done, total, next);
    }

    @Transactional
    public CursosEnrollment updateStatus(UUID companyId, UUID id, String newStatusId) {
        CursoEnrollmentStatus newStatus = CursoEnrollmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        CursosEnrollment current = enrollmentRepository.findById(companyId, id)
            .orElseThrow(EnrollmentNotFoundException::new);
        CursoEnrollmentStatus from = CursoEnrollmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // concluida E cancelada materializam end_date = hoje.
        LocalDate endDate = newStatus.isTerminal() ? LocalDate.now(TENANT_ZONE) : null;
        enrollmentRepository.updateStatus(companyId, id, newStatus.id(), endDate);

        // notifica ativa (retomada) / concluida / cancelada; trancada silenciosa.
        String text = newStatus.notificationText(current.studentName(), current.courseTitle());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        // Onda 1 (backlog #1): CONCLUIDA emite o certificado (código único) e envia o link/código
        // — a IA só entrega o que o backend gerou (trava intacta).
        if (newStatus == CursoEnrollmentStatus.CONCLUIDA) {
            var config = configRepository.findByCompany(companyId);
            String schoolName = jdbcTemplate.query(
                    "select name from companies where id = ?",
                    (rs, rn) -> rs.getString("name"), companyId)
                .stream().findFirst().orElse(null);
            String code = certificateService.issue(companyId, id, current.studentName(),
                current.courseTitle(), schoolName);
            StringBuilder cert = new StringBuilder("🎓 Seu CERTIFICADO de conclusão está pronto! ");
            if (config.certificateBaseUrl() != null && !config.certificateBaseUrl().isBlank()) {
                cert.append("Acesse e compartilhe: ")
                    .append(config.certificateBaseUrl().replaceAll("/+$", ""))
                    .append("/public/cursos/certificados/").append(code);
            } else {
                cert.append("Código de verificação: ").append(code)
                    .append(" (a escola te passa o link de acesso).");
            }
            notifier.notifyStatus(companyId, current.conversationId(), cert.toString());
        }

        contextCache.invalidate(companyId);
        return enrollmentRepository.findById(companyId, id).orElseThrow(EnrollmentNotFoundException::new);
    }
}
