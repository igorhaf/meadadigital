package com.meada.whatsapp.profiles.dermatologia.professionals;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos dermatologistas (camada 8.11). Audita + invalida {@link DermatologiaContextCache}. */
@Service
public class DermatologiaProfessionalService {

    private final DermatologiaProfessionalRepository repository;
    private final AuditLogger auditLogger;
    private final DermatologiaContextCache contextCache;

    public DermatologiaProfessionalService(DermatologiaProfessionalRepository repository, AuditLogger auditLogger,
                                           DermatologiaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ProfessionalInUseException extends RuntimeException {}

    @Transactional
    public DermatologiaProfessional create(UUID companyId, UUID userId, String name, String specialty, String crmRqe, String notes) {
        DermatologiaProfessional created = repository.insert(companyId, name, specialty, crmRqe, notes);
        auditLogger.log(companyId, userId, "dermatologia_professional_created", "dermatologia_professional",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public DermatologiaProfessional update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                           String crmRqe, String notes, Boolean active) {
        DermatologiaProfessional updated = repository.update(companyId, id, name, specialty, crmRqe, notes, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "dermatologia_professional_updated", "dermatologia_professional", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public DermatologiaProfessional toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        DermatologiaProfessional p = repository.toggle(companyId, id, active).orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "dermatologia_professional_updated", "dermatologia_professional", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return p;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new ProfessionalNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ProfessionalInUseException();
        }
        auditLogger.log(companyId, userId, "dermatologia_professional_deleted", "dermatologia_professional", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<DermatologiaProfessional> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<DermatologiaProfessional> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
