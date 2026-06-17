package com.meada.whatsapp.profiles.pet.services;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.pet.PetProfileGuard;
import com.meada.whatsapp.profiles.pet.PetProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.pet.services.PetServiceService.InvalidSpeciesException;
import com.meada.whatsapp.profiles.pet.services.PetServiceService.ServiceInUseException;
import com.meada.whatsapp.profiles.pet.services.PetServiceService.ServiceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.UUID;

/** Serviços do tenant pet (camada 7.8). TENANT + perfil 'pet' only. */
@RestController
public class PetServiceController {

    private final PetServiceService service;
    private final PetProfileGuard profileGuard;

    public PetServiceController(PetServiceService service, PetProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 200) String name,
        String category,
        @Min(15) @Max(240) int durationMinutes,
        Integer priceCents,
        String speciesRestriction,
        String description) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/pet/services")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(defaultValue = "false") boolean onlyActive) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, onlyActive)));
    }

    @GetMapping("/api/pet/services/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "service_not_found"));
    }

    @PostMapping("/api/pet/services")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.name(),
                req.category(), req.durationMinutes(), req.priceCents(), req.speciesRestriction(), req.description()));
        } catch (InvalidSpeciesException e) {
            return error(400, "Bad Request", "invalid_species");
        }
    }

    /**
     * PATCH parcial. Usa JsonNode pra distinguir "campo ausente" de "speciesRestriction:null"
     * (permite limpar a restrição de espécie). Demais campos seguem o padrão de PATCH parcial.
     */
    @PatchMapping("/api/pet/services/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody JsonNode body) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        String name = body.hasNonNull("name") ? body.get("name").asText() : null;
        String category = body.has("category") ? (body.get("category").isNull() ? "" : body.get("category").asText()) : null;
        Integer duration = body.hasNonNull("durationMinutes") ? body.get("durationMinutes").asInt() : null;
        Integer price = body.has("priceCents")
            ? (body.get("priceCents").isNull() ? -1 : body.get("priceCents").asInt())
            : null;
        boolean speciesProvided = body.has("speciesRestriction");
        String species = speciesProvided && !body.get("speciesRestriction").isNull()
            ? body.get("speciesRestriction").asText() : null;
        String description = body.has("description") ? (body.get("description").isNull() ? "" : body.get("description").asText()) : null;
        Boolean active = body.hasNonNull("active") ? body.get("active").asBoolean() : null;
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, name, category, duration,
                price, species, speciesProvided, description, active));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (InvalidSpeciesException e) {
            return error(400, "Bad Request", "invalid_species");
        }
    }

    @PatchMapping("/api/pet/services/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requirePet(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        }
    }

    @DeleteMapping("/api/pet/services/{id}")
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
        } catch (ServiceNotFoundException e) {
            return error(404, "Not Found", "service_not_found");
        } catch (ServiceInUseException e) {
            return error(409, "Conflict", "service_in_use");
        }
    }
}
