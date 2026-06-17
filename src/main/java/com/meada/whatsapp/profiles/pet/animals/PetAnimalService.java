package com.meada.whatsapp.profiles.pet.animals;

import com.meada.whatsapp.common.audit.AuditLogger;
import com.meada.whatsapp.profiles.pet.PetContextCache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras dos animais (camada 7.8). Sub-entidade do tutor. Valida o contato (tutor) ao criar, audita
 * e invalida o {@link PetContextCache}. DELETE protegido por FK (appointment) → 409 animal_in_use;
 * o caminho preferido pra "remover" é {@link #archive} (active=false).
 */
@Service
public class PetAnimalService {

    private final PetAnimalRepository repository;
    private final AuditLogger auditLogger;
    private final PetContextCache contextCache;

    public PetAnimalService(PetAnimalRepository repository, AuditLogger auditLogger, PetContextCache contextCache) {
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.contextCache = contextCache;
    }

    public static class AnimalNotFoundException extends RuntimeException {}
    public static class ContactNotFoundException extends RuntimeException {}
    public static class AnimalInUseException extends RuntimeException {}
    public static class InvalidSpeciesException extends RuntimeException {}

    private static void validateSpecies(String s) {
        if (s == null || (!s.equals("cao") && !s.equals("gato") && !s.equals("outro"))) {
            throw new InvalidSpeciesException();
        }
    }

    @Transactional
    public PetAnimal create(UUID companyId, UUID userId, UUID contactId, String name, String species,
                            String breed, String sex, Integer birthYear, String notes) {
        if (!repository.contactExists(companyId, contactId)) {
            throw new ContactNotFoundException();
        }
        validateSpecies(species);
        PetAnimal created = repository.insert(companyId, contactId, name, species, breed, sex, birthYear, notes);
        auditLogger.log(companyId, userId, "pet_animal_created", "pet_animal",
            created.id(), Map.of("name", created.name(), "species", created.species()));
        contextCache.invalidate(companyId);
        return created;
    }

    @Transactional
    public PetAnimal update(UUID companyId, UUID userId, UUID id, String name, String species, String breed,
                            String sex, Integer birthYear, String notes, Boolean active) {
        if (species != null && !species.isBlank()) {
            validateSpecies(species);
        }
        PetAnimal updated = repository.update(companyId, id, name, species, breed, sex, birthYear, notes, active)
            .orElseThrow(AnimalNotFoundException::new);
        auditLogger.log(companyId, userId, "pet_animal_updated", "pet_animal", id, Map.of());
        contextCache.invalidate(companyId);
        return updated;
    }

    @Transactional
    public PetAnimal archive(UUID companyId, UUID userId, UUID id) {
        PetAnimal a = repository.archive(companyId, id).orElseThrow(AnimalNotFoundException::new);
        auditLogger.log(companyId, userId, "pet_animal_archived", "pet_animal", id, Map.of());
        contextCache.invalidate(companyId);
        return a;
    }

    @Transactional
    public void delete(UUID companyId, UUID userId, UUID id) {
        try {
            if (!repository.delete(companyId, id)) {
                throw new AnimalNotFoundException();
            }
        } catch (DataIntegrityViolationException e) {
            throw new AnimalInUseException();
        }
        auditLogger.log(companyId, userId, "pet_animal_deleted", "pet_animal", id, Map.of());
        contextCache.invalidate(companyId);
    }

    public List<PetAnimal> list(UUID companyId, UUID contactId, String species, Boolean active, String search) {
        return repository.listByCompany(companyId, contactId, species, active, search);
    }

    public Optional<PetAnimal> get(UUID companyId, UUID id) {
        return repository.findById(companyId, id);
    }
}
