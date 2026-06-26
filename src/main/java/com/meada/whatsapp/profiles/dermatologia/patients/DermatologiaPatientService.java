package com.meada.whatsapp.profiles.dermatologia.patients;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos pacientes (camada 8.11). Sub-entidade do contact. Valida o cliente ao criar, audita e
 * invalida o {@link DermatologiaContextCache}. DELETE protegido por FK (consulta via restrict) →
 * 409 patient_in_use; o caminho preferido pra "remover" é {@link #archive} (active=false).
 */
@Service
public class DermatologiaPatientService {

    private final DermatologiaPatientRepository repository;
    private final AuditLogger auditLogger;
    private final DermatologiaContextCache contextCache;

    public DermatologiaPatientService(DermatologiaPatientRepository repository, AuditLogger auditLogger, DermatologiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class PatientNotFoundException extends RuntimeException {}
    public static class ContactNotFoundException extends RuntimeException {}
    public static class PatientInUseException extends RuntimeException {}

    @Transactional
    public DermatologiaPatient create(UUID companyId, UUID userId, UUID contactId, String name, LocalDate birthDate, String notes) {
        if (!repository.contactExists(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        DermatologiaPatient created = repository.insert(companyId, contactId, name, birthDate, notes);
        auditLogger.log(companyId, userId, "dermatologia_patient_created", "dermatologia_patient",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public DermatologiaPatient update(UUID companyId, UUID userId, UUID id, String name, LocalDate birthDate,
                                      boolean birthProvided, String notes, Boolean active) {
        DermatologiaPatient updated = repository.update(companyId, id, name, birthDate, birthProvided, notes, active)
            .orElseThrow(PatientNotFoundException::new);
        auditLogger.log(companyId, userId, "dermatologia_patient_updated", "dermatologia_patient", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public DermatologiaPatient archive(UUID companyId, UUID userId, UUID id) {
        DermatologiaPatient p = repository.archive(companyId, id).orElseThrow(PatientNotFoundException::new);
        auditLogger.log(companyId, userId, "dermatologia_patient_archived", "dermatologia_patient", id, Map.of());
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new PatientNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new PatientInUseException();
        }
        auditLogger.log(companyId, userId, "dermatologia_patient_deleted", "dermatologia_patient", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<DermatologiaPatient> list(UUID companyId, UUID contactId, Boolean active, String search) {
        return repository.listByCompany(companyId, contactId, active, search);
    }

    public Optional<DermatologiaPatient> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
