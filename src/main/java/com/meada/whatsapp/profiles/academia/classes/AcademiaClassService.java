package com.meada.whatsapp.profiles.academia.classes;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.academia.AcademiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras das aulas da academia (camada 7.7). Audita mutações e invalida o {@link AcademiaContextCache}.
 */
@Service
public class AcademiaClassService {

    private final AcademiaClassRepository repository;
    private final AuditLogger auditLogger;
    private final AcademiaContextCache contextCache;

    public AcademiaClassService(AcademiaClassRepository repository, AuditLogger auditLogger,
                                AcademiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    /** Aula não encontrada / de outro tenant (→ 404). */
    public static class ClassNotFoundException extends RuntimeException {}

    /** Aula referenciada por matrícula (FK restrict) — não pode hard-deletar (→ 409). */
    public static class ClassInUseException extends RuntimeException {}

    @Transactional
    public AcademiaClass create(UUID companyId, UUID userId, String name, String modality, int dayOfWeek,
                                LocalTime startTime, int durationMinutes, int capacity, String instructor) {
        AcademiaClass created = repository.insert(companyId, name, modality, dayOfWeek, startTime,
            durationMinutes, capacity, instructor);
        auditLogger.log(companyId, userId, "academia_class_created", "academia_class",
            created.id(), Map.of("name", created.name(), "modality", created.modality()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AcademiaClass update(UUID companyId, UUID userId, UUID id, String name, String modality,
                                Integer dayOfWeek, LocalTime startTime, Integer durationMinutes,
                                Integer capacity, String instructor, Boolean active) {
        AcademiaClass updated = repository.update(companyId, id, name, modality, dayOfWeek, startTime,
                durationMinutes, capacity, instructor, active)
            .orElseThrow(ClassNotFoundException::new);
        auditLogger.log(companyId, userId, "academia_class_updated", "academia_class", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AcademiaClass toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AcademiaClass c = repository.toggle(companyId, id, active).orElseThrow(ClassNotFoundException::new);
        auditLogger.log(companyId, userId, "academia_class_updated", "academia_class", id, Map.of("active", active));
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
        auditLogger.log(companyId, userId, "academia_class_deleted", "academia_class", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<AcademiaClass> list(UUID companyId, boolean onlyActive, Integer dayOfWeek) {
        return repository.listByCompany(companyId, onlyActive, dayOfWeek);
    }

    public Optional<AcademiaClass> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    /** Vagas restantes (capacity - matrículas ativas/suspensas). */
    public int remainingSlots(AcademiaClass c) {
        return Math.max(0, c.capacity() - repository.countActiveMembers(c.id()));
    }
}
