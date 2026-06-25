package com.meada.whatsapp.profiles.escola.classes;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.escola.EscolaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das turmas da escola (camada 8.19). Valida o turno (manha/tarde/integral), audita mutações
 * e invalida o {@link EscolaContextCache}. DELETE protegido por FK (matrícula) → 409 class_in_use.
 */
@Service
public class EscolaClassService {

    private final EscolaClassRepository repository;
    private final AuditLogger auditLogger;
    private final EscolaContextCache contextCache;

    public EscolaClassService(EscolaClassRepository repository, AuditLogger auditLogger,
                              EscolaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Turma não encontrada / de outro tenant (→ 404). */
    public static class ClassNotFoundException extends RuntimeException {}

    /** Turma referenciada por matrícula (FK restrict) — não pode hard-deletar (→ 409 class_in_use). */
    public static class ClassInUseException extends RuntimeException {}

    /** Turno fora de manha/tarde/integral (→ 400 invalid_shift). */
    public static class InvalidShiftException extends RuntimeException {}

    private static void validateShift(String s) {
        if (s == null || (!s.equals("manha") && !s.equals("tarde") && !s.equals("integral"))) {
            throw new InvalidShiftException();
        }
    }

    @Transactional
    public EscolaClass create(UUID companyId, UUID userId, String name, String grade, String shift,
                              int capacity, int monthlyCents, Integer year, String description) {
        validateShift(shift);
        EscolaClass created = repository.insert(companyId, name, grade, shift, capacity, monthlyCents, year, description);
        auditLogger.log(companyId, userId, "escola_class_created", "escola_class",
            created.id(), Map.of("name", created.name(), "grade", created.grade()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public EscolaClass update(UUID companyId, UUID userId, UUID id, String name, String grade, String shift,
                              Integer capacity, Integer monthlyCents, Integer year, String description,
                              Boolean active) {
        if (shift != null && !shift.isBlank()) {
            validateShift(shift);
        }
        EscolaClass updated = repository.update(companyId, id, name, grade, shift, capacity, monthlyCents,
                year, description, active)
            .orElseThrow(ClassNotFoundException::new);
        auditLogger.log(companyId, userId, "escola_class_updated", "escola_class", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public EscolaClass toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        EscolaClass c = repository.toggle(companyId, id, active).orElseThrow(ClassNotFoundException::new);
        auditLogger.log(companyId, userId, "escola_class_updated", "escola_class", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return c;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new ClassNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ClassInUseException();
        }
        auditLogger.log(companyId, userId, "escola_class_deleted", "escola_class", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<EscolaClass> list(UUID companyId, boolean onlyActive, String shift) {
        return repository.listByCompany(companyId, onlyActive, shift);
    }

    public Optional<EscolaClass> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    /** Vagas restantes (capacity - matrículas ativas/suspensas). */
    public int remainingSlots(EscolaClass c) {
        return Math.max(0, c.capacity() - repository.countActiveEnrollments(c.id()));
    }
}
