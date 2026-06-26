package com.meada.whatsapp.profiles.dermatologia.patients;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaProfileGuard;
import com.meada.whatsapp.profiles.dermatologia.DermatologiaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientService.ContactNotFoundException;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientService.PatientInUseException;
import com.meada.whatsapp.profiles.dermatologia.patients.DermatologiaPatientService.PatientNotFoundException;
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

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Pacientes do tenant dermatologia (camada 8.11) — sub-entidade do contact. TENANT + perfil
 * 'dermatologia' only. CRUD + archive (preferido a delete) + delete (409 se houver consulta).
 */
@RestController
public class DermatologiaPatientController {

    private final DermatologiaPatientService service;
    private final DermatologiaProfileGuard profileGuard;

    public DermatologiaPatientController(DermatologiaPatientService service, DermatologiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 120) String name,
        String birthDate,
        String notes) {}

    public record UpdateRequest(
        @Size(max = 120) String name,
        String birthDate,
        Boolean clearBirthDate,
        String notes,
        Boolean active) {}

    @GetMapping("/api/dermatologia/patients")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, contactId, active, search)));
    }

    @GetMapping("/api/dermatologia/patients/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "patient_not_found"));
    }

    @PostMapping("/api/dermatologia/patients")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        LocalDate birthDate;
        try {
            birthDate = req.birthDate() == null || req.birthDate().isBlank() ? null : LocalDate.parse(req.birthDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            DermatologiaPatient created = service.create(companyId, user.userId(), req.contactId(), req.name(),
                birthDate, req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        }
    }

    @PatchMapping("/api/dermatologia/patients/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean birthProvided = req.birthDate() != null || Boolean.TRUE.equals(req.clearBirthDate());
        LocalDate birthDate;
        try {
            birthDate = Boolean.TRUE.equals(req.clearBirthDate()) || req.birthDate() == null
                ? null : LocalDate.parse(req.birthDate());
        } catch (DateTimeException e) {
            return error(400, "Bad Request", "invalid_date");
        }
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                birthDate, birthProvided, req.notes(), req.active()));
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        }
    }

    @PatchMapping("/api/dermatologia/patients/{id}/archive")
    public ResponseEntity<Object> archive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.archive(companyId, user.userId(), id));
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        }
    }

    @DeleteMapping("/api/dermatologia/patients/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireDermatologia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (PatientNotFoundException e) {
            return error(404, "Not Found", "patient_not_found");
        } catch (PatientInUseException e) {
            return error(409, "Conflict", "patient_in_use");
        }
    }
}
