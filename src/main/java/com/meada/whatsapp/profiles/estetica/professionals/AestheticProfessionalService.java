package com.meada.whatsapp.profiles.estetica.professionals;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.estetica.EsteticaContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos profissionais do tenant estetica (camada 8.3). Audita + invalida {@link EsteticaContextCache}. Clone salon. */
@Service
public class AestheticProfessionalService {

    private final AestheticProfessionalRepository repository;
    private final AuditLogger auditLogger;
    private final EsteticaContextCache contextCache;

    public AestheticProfessionalService(AestheticProfessionalRepository repository, AuditLogger auditLogger,
                                        EsteticaContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ProfessionalNotFoundException extends RuntimeException {}
    public static class ProfessionalInUseException extends RuntimeException {}

    @Transactional
    public AestheticProfessional create(UUID companyId, UUID userId, String name, String specialty, String notes) {
        AestheticProfessional created = repository.insert(companyId, name, specialty, notes);
        auditLogger.log(companyId, userId, "aesthetic_professional_created", "aesthetic_professional",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public AestheticProfessional update(UUID companyId, UUID userId, UUID id, String name, String specialty,
                                        String notes, Boolean active) {
        AestheticProfessional updated = repository.update(companyId, id, name, specialty, notes, active)
            .orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "aesthetic_professional_updated", "aesthetic_professional", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public AestheticProfessional toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        AestheticProfessional p = repository.toggle(companyId, id, active).orElseThrow(ProfessionalNotFoundException::new);
        auditLogger.log(companyId, userId, "aesthetic_professional_updated", "aesthetic_professional", id, Map.of("active", active));
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
        auditLogger.log(companyId, userId, "aesthetic_professional_deleted", "aesthetic_professional", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<AestheticProfessional> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<AestheticProfessional> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
