package com.meada.whatsapp.profiles.escola.students;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.escola.EscolaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos alunos (camada 8.19). Sub-entidade do responsável. Valida o contato (responsável) ao
 * criar, audita e invalida o {@link EscolaContextCache}. DELETE protegido por FK (matrícula) → 409
 * student_in_use; o caminho preferido pra "remover" é {@link #archive} (active=false).
 */
@Service
public class EscolaStudentService {

    private final EscolaStudentRepository repository;
    private final AuditLogger auditLogger;
    private final EscolaContextCache contextCache;

    public EscolaStudentService(EscolaStudentRepository repository, AuditLogger auditLogger,
                                EscolaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class StudentNotFoundException extends RuntimeException {}
    public static class ContactNotFoundException extends RuntimeException {}
    public static class StudentInUseException extends RuntimeException {}

    @Transactional
    public EscolaStudent create(UUID companyId, UUID userId, UUID contactId, String name,
                                LocalDate birthDate, String intendedGrade, String notes) {
        if (contactId == null || !repository.contactExists(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        EscolaStudent created = repository.insert(companyId, contactId, name, birthDate, intendedGrade, notes);
        auditLogger.log(companyId, userId, "escola_student_created", "escola_student",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public EscolaStudent update(UUID companyId, UUID userId, UUID id, String name, LocalDate birthDate,
                                String intendedGrade, String notes, Boolean active) {
        EscolaStudent updated = repository.update(companyId, id, name, birthDate, intendedGrade, notes, active)
            .orElseThrow(StudentNotFoundException::new);
        auditLogger.log(companyId, userId, "escola_student_updated", "escola_student", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public EscolaStudent archive(UUID companyId, UUID userId, UUID id) {
        EscolaStudent s = repository.archive(companyId, id).orElseThrow(StudentNotFoundException::new);
        auditLogger.log(companyId, userId, "escola_student_archived", "escola_student", id, Map.of());
        contextCache.invalidate(companyId);
        return s;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new StudentNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new StudentInUseException();
        }
        auditLogger.log(companyId, userId, "escola_student_deleted", "escola_student", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<EscolaStudent> list(UUID companyId, UUID contactId, Boolean active, String search) {
        return repository.listByCompany(companyId, contactId, active, search);
    }

    public Optional<EscolaStudent> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
