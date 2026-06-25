package com.meada.whatsapp.profiles.escola.enrollments;

import com.meada.whatsapp.profiles.escola.EscolaContextCache;
import com.meada.whatsapp.profiles.escola.EscolaEnrollmentStatus;
import com.meada.whatsapp.profiles.escola.classes.EscolaClass;
import com.meada.whatsapp.profiles.escola.classes.EscolaClassRepository;
import com.meada.whatsapp.profiles.escola.students.EscolaStudent;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das matrículas da escola (camada 8.19).
 *
 * <p>{@link #create} valida a turma (ativa, do company), o aluno (existe, ativo, do company), e
 * delega ao repo, que valida ANTI-DUPLA (1 ativa por aluno+turma) e CAPACITY por turma DENTRO da
 * transação. Status inicial = ativa; notifica boas-vindas. {@link #updateStatus} valida a transição;
 * em cancelada materializa end_date e libera a vaga; notifica ativa/cancelada.
 */
@Service
public class EscolaEnrollmentService {

    static final ZoneId TENANT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final EscolaEnrollmentRepository enrollmentRepository;
    private final EscolaClassRepository classRepository;
    private final EscolaStudentRepository studentRepository;
    private final EscolaEnrollmentNotifier notifier;
    private final EscolaContextCache contextCache;

    public EscolaEnrollmentService(EscolaEnrollmentRepository enrollmentRepository,
                                   EscolaClassRepository classRepository,
                                   EscolaStudentRepository studentRepository,
                                   EscolaEnrollmentNotifier notifier,
                                   EscolaContextCache contextCache) {
        this.enrollmentRepository = enrollmentRepository;
        this.classRepository = classRepository;
        this.studentRepository = studentRepository;
        this.notifier = notifier;
        this.contextCache = contextCache;
    }

    public static class EnrollmentNotFoundException extends RuntimeException {}
    public static class ClassNotFoundException extends RuntimeException {}
    public static class ClassInactiveException extends RuntimeException {}
    public static class StudentNotFoundException extends RuntimeException {}
    public static class StudentInactiveException extends RuntimeException {}
    public static class AlreadyActiveException extends RuntimeException {}
    public static class InvalidStatusException extends RuntimeException {}
    public static class InvalidStatusTransitionException extends RuntimeException {}

    /** Turma sem vaga (→ 409 class_full). Carrega classId + className. */
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

    /**
     * Cria a matrícula. Valida turma ativa + aluno ativo (do company); delega ao repo (anti-dupla +
     * capacity transacional). Status ativa + notificação de boas-vindas. O nome do responsável é
     * resolvido do contato (snapshot opcional).
     */
    @Transactional
    public EscolaEnrollment create(UUID companyId, UUID classId, UUID studentId, UUID contactId,
                                   UUID conversationId, String notes) {
        EscolaClass clazz = classRepository.findById(companyId, classId).orElseThrow(ClassNotFoundException::new);
        if (!clazz.active()) {
            throw new ClassInactiveException();
        }
        EscolaStudent student = studentRepository.findById(companyId, studentId).orElseThrow(StudentNotFoundException::new);
        if (!student.active()) {
            throw new StudentInactiveException();
        }
        String responsibleName = contactId != null
            ? studentRepository.contactName(companyId, contactId).orElse(null)
            : studentRepository.contactName(companyId, student.contactId()).orElse(null);

        EscolaEnrollment created;
        try {
            created = enrollmentRepository.insertEnrollment(companyId, clazz, student.id(), student.name(),
                responsibleName, conversationId, contactId, notes);
        } catch (EscolaEnrollmentRepository.AlreadyActiveException e) {
            throw new AlreadyActiveException();
        } catch (EscolaEnrollmentRepository.ClassFullException e) {
            throw new ClassFullException(e.classId(), e.className());
        }
        // boas-vindas (status inicial ativa).
        notifier.notifyStatus(companyId, conversationId,
            EscolaEnrollmentStatus.ATIVA.notificationText(created.studentName(), created.className(),
                created.classGrade(), created.classShift()));
        contextCache.invalidate(companyId);
        return created;
    }

    public List<EscolaEnrollment> list(UUID companyId, String status, UUID classId, UUID studentId,
                                       UUID contactId, int limit, int offset) {
        return enrollmentRepository.listByCompany(companyId, status, classId, studentId, contactId, limit, offset);
    }

    public long count(UUID companyId, String status, UUID classId, UUID studentId, UUID contactId) {
        return enrollmentRepository.countByCompany(companyId, status, classId, studentId, contactId);
    }

    public Optional<EscolaEnrollment> get(UUID companyId, UUID id) {
        return enrollmentRepository.findById(companyId, id);
    }

    @Transactional
    public EscolaEnrollment updateStatus(UUID companyId, UUID id, String newStatusId) {
        EscolaEnrollmentStatus newStatus = EscolaEnrollmentStatus.fromId(newStatusId)
            .orElseThrow(InvalidStatusException::new);

        EscolaEnrollment current = enrollmentRepository.findById(companyId, id)
            .orElseThrow(EnrollmentNotFoundException::new);
        EscolaEnrollmentStatus from = EscolaEnrollmentStatus.fromId(current.status())
            .orElseThrow(InvalidStatusException::new);

        if (!from.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException();
        }

        // cancelada materializa end_date = hoje (e libera a vaga, pois count filtra <> cancelada).
        LocalDate endDate = newStatus == EscolaEnrollmentStatus.CANCELADA ? LocalDate.now(TENANT_ZONE) : null;
        enrollmentRepository.updateStatus(companyId, id, newStatus.id(), endDate);

        // notifica ativa (retomada) / cancelada; suspensa silenciosa.
        String text = newStatus.notificationText(current.studentName(), current.className(),
            current.classGrade(), current.classShift());
        notifier.notifyStatus(companyId, current.conversationId(), text);

        contextCache.invalidate(companyId);
        return enrollmentRepository.findById(companyId, id).orElseThrow(EnrollmentNotFoundException::new);
    }
}
