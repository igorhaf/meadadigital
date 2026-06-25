package com.meada.whatsapp.profiles.escola.students;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard;
import com.meada.whatsapp.profiles.escola.EscolaProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentService.ContactNotFoundException;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentService.StudentInUseException;
import com.meada.whatsapp.profiles.escola.students.EscolaStudentService.StudentNotFoundException;
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
 * Alunos do tenant escola (camada 8.19) — sub-entidade do responsável (contact). TENANT + perfil
 * 'escola' only. CRUD + archive (preferido a delete) + delete (409 se houver matrícula). GET aceita
 * ?contactId= para listar os filhos de um responsável.
 */
@RestController
public class EscolaStudentController {

    private final EscolaStudentService service;
    private final EscolaProfileGuard profileGuard;

    public EscolaStudentController(EscolaStudentService service, EscolaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateStudentRequest(
        @NotNull UUID contactId,
        @NotBlank @Size(max = 200) String name,
        String birthDate,
        String intendedGrade,
        String notes) {}

    public record UpdateStudentRequest(
        @Size(max = 200) String name,
        String birthDate,
        String intendedGrade,
        String notes,
        Boolean active) {}

    @GetMapping("/api/escola/students")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, contactId, active, search)));
    }

    @GetMapping("/api/escola/students/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "student_not_found"));
    }

    @PostMapping("/api/escola/students")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateStudentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
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
            EscolaStudent created = service.create(companyId, user.userId(), req.contactId(), req.name(),
                birthDate, req.intendedGrade(), req.notes());
            return ResponseEntity.status(201).body(created);
        } catch (ContactNotFoundException e) {
            return error(404, "Not Found", "contact_not_found");
        }
    }

    @PatchMapping("/api/escola/students/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateStudentRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
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
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(), birthDate,
                req.intendedGrade(), req.notes(), req.active()));
        } catch (StudentNotFoundException e) {
            return error(404, "Not Found", "student_not_found");
        }
    }

    @PatchMapping("/api/escola/students/{id}/archive")
    public ResponseEntity<Object> archive(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.archive(companyId, user.userId(), id));
        } catch (StudentNotFoundException e) {
            return error(404, "Not Found", "student_not_found");
        }
    }

    @DeleteMapping("/api/escola/students/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireEscola(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (StudentNotFoundException e) {
            return error(404, "Not Found", "student_not_found");
        } catch (StudentInUseException e) {
            return error(409, "Conflict", "student_in_use");
        }
    }
}
