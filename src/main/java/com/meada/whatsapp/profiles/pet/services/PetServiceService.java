package com.meada.whatsapp.profiles.pet.services;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.pet.PetContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Regras dos serviços do pet (camada 7.8). Audita + invalida {@link PetContextCache}. */
@Service
public class PetServiceService {

    private final PetServiceRepository repository;
    private final AuditLogger auditLogger;
    private final PetContextCache contextCache;

    public PetServiceService(PetServiceRepository repository, AuditLogger auditLogger,
                             PetContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class ServiceNotFoundException extends RuntimeException {}
    public static class ServiceInUseException extends RuntimeException {}
    public static class InvalidSpeciesException extends RuntimeException {}

    private static void validateSpecies(String s) {
        if (s != null && !s.isBlank() && !s.equals("cao") && !s.equals("gato") && !s.equals("outro")) {
            throw new InvalidSpeciesException();
        }
    }

    @Transactional
    public PetService create(UUID companyId, UUID userId, String name, String category, int durationMinutes,
                             Integer priceCents, String speciesRestriction, String description) {
        validateSpecies(speciesRestriction);
        String species = (speciesRestriction != null && speciesRestriction.isBlank()) ? null : speciesRestriction;
        PetService created = repository.insert(companyId, name, category, durationMinutes, priceCents, species, description);
        auditLogger.log(companyId, userId, "pet_service_created", "pet_service",
            created.id(), Map.of("name", created.name()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PetService update(UUID companyId, UUID userId, UUID id, String name, String category,
                             Integer durationMinutes, Integer priceCents, String speciesRestriction,
                             boolean speciesProvided, String description, Boolean active) {
        if (speciesProvided) {
            validateSpecies(speciesRestriction);
        }
        PetService updated = repository.update(companyId, id, name, category, durationMinutes, priceCents,
                speciesRestriction, speciesProvided, description, active)
            .orElseThrow(ServiceNotFoundException::new);
        auditLogger.log(companyId, userId, "pet_service_updated", "pet_service", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PetService toggle(UUID companyId, UUID userId, UUID id, boolean active) {
        PetService s = repository.toggle(companyId, id, active).orElseThrow(ServiceNotFoundException::new);
        auditLogger.log(companyId, userId, "pet_service_updated", "pet_service", id, Map.of("active", active));
        contextCache.invalidate(companyId);
        return s;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new ServiceNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new ServiceInUseException();
        }
        auditLogger.log(companyId, userId, "pet_service_deleted", "pet_service", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<PetService> list(UUID companyId, boolean onlyActive) {
        return repository.listByCompany(companyId, onlyActive);
    }

    public Optional<PetService> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
