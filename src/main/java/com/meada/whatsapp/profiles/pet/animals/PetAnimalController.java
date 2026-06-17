package com.meada.whatsapp.profiles.pet.animals;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.pet.PetProfileGuard;
import com.meada.whatsapp.profiles.pet.PetProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.AnimalInUseException;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.AnimalNotFoundException;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.ContactNotFoundException;
import com.meada.whatsapp.profiles.pet.animals.PetAnimalService.InvalidSpeciesException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Animais do tenant pet (camada 7.8) — sub-entidade do tutor (contact). TENANT + perfil 'pet' only.
 * CRUD + archive (preferido a delete) + delete (409 se houver agendamento).
 */
@RestController
public class PetAnimalController {

    private static final int MAX_PAGE_SIZE = 200;

    private final PetAnimalService service;
    private final PetProfileGuard profileGuard;

    public PetAnimalController(PetAnimalService service, PetProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateAnimalRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 100) String name,
        @NotBlank String species,
        String breed,
        String sex,
        Integer birthYear,
        String notes) {}

    public record UpdateAnimalRequest(
        @Size(max = 100) String name,
        String species,
        String breed,
        String sex,
        Integer birthYear,
        String notes,
        Boolean active) {}

    @GetMapping("/api/pet/animals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String species,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, contactId, species, active, search)));
    }

    @GetMapping("/api/pet/animals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "animal_not_found"));
    }

    @PostMapping("/api/pet/animals")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateAnimalRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            PetAnimal created = service.create(companyId, user.userId(), req.contactId(), req.name(),
                req.species(), req.breed(), req.sex(), req.birthYear(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        } catch (InvalidSpeciesException e) {
            return error(400, "Bad Request", "invalid_species");
        }
    }

    @PatchMapping("/api/pet/animals/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateAnimalRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), req.species(),
                req.breed(), req.sex(), req.birthYear(), req.notes(), req.active()));
        } catch (AnimalNotFoundException e) {
            return error(404, "Not Found", "animal_not_found");
        } catch (InvalidSpeciesException e) {
            return error(400, "Bad Request", "invalid_species");
        }
    }

    @PatchMapping("/api/pet/animals/{id}/archive")
    public ResponseEntity<Object> archive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.archive(companyId, user.userId(), id));
        } catch (AnimalNotFoundException e) {
            return error(404, "Not Found", "animal_not_found");
        }
    }

    @DeleteMapping("/api/pet/animals/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (AnimalNotFoundException e) {
            return error(404, "Not Found", "animal_not_found");
        } catch (AnimalInUseException e) {
            return error(409, "Conflict", "animal_in_use");
        }
    }
}
