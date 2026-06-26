package com.meada.whatsapp.profiles.dermatologia.proceduretypes;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos tipos de atendimento (camada 8.11, ESCAPADA). Valida a duração (5..480 → 400
 * invalid_duration), audita e invalida o {@link DermatologiaContextCache}. DELETE protegido por FK
 * (consulta via restrict) → 409 procedure_type_in_use.
 */
@Service
public class DermatologiaProcedureTypeService {

    private static final int MIN_DURATION = 5;
    private static final int MAX_DURATION = 480;

    private final DermatologiaProcedureTypeRepository repository;
    private final AuditLogger auditLogger;
    private final DermatologiaContextCache contextCache;

    public DermatologiaProcedureTypeService(DermatologiaProcedureTypeRepository repository, AuditLogger auditLogger,
                                            DermatologiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProcedureTypeNotFoundException extends RuntimeException {}
    public static class ProcedureTypeInUseException extends RuntimeException {}
    public static class InvalidDurationException extends RuntimeException {}

    @Transactional
    public DermatologiaProcedureType create(UUID companyId, UUID userId, String name, int durationMinutes,
                                            String prepInstructions, String notes) {
        requireValidDuration(durationMinutes);
        DermatologiaProcedureType created = repository.insert(companyId, name, durationMinutes, prepInstructions, notes);
        auditLogger.log(companyId, userId, "dermatologia_procedure_type_created", "dermatologia_procedure_type",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public DermatologiaProcedureType update(UUID companyId, UUID userId, UUID id, String name, Integer durationMinutes,
                                            String prepInstructions, boolean prepProvided, String notes, Boolean active) {
        if (durationMinutes != null) {
            requireValidDuration(durationMinutes);
        }
        DermatologiaProcedureType updated = repository.update(companyId, id, name, durationMinutes, prepInstructions, prepProvided, notes, active)
            .orElseThrow(ProcedureTypeNotFoundException::new);
        auditLogger.log(companyId, userId, "dermatologia_procedure_type_updated", "dermatologia_procedure_type", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public DermatologiaProcedureType toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        DermatologiaProcedureType p = repository.toggle(companyId, id, active).orElseThrow(ProcedureTypeNotFoundException::new);
        auditLogger.log(companyId, userId, "dermatologia_procedure_type_updated", "dermatologia_procedure_type", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new ProcedureTypeNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ProcedureTypeInUseException();
        }
        auditLogger.log(companyId, userId, "dermatologia_procedure_type_deleted", "dermatologia_procedure_type", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<DermatologiaProcedureType> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<DermatologiaProcedureType> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }

    private static void requireValidDuration(int durationMinutes) {
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION) {
            throw new InvalidDurationException();
        }
    }
}
