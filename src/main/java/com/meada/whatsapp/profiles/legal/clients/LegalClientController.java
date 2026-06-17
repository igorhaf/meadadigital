package com.meada.whatsapp.profiles.legal.clients;

import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.legal.LegalProfileGuard;
import com.meada.whatsapp.profiles.legal.LegalProfileGuard.WrongProfileException;
import com.meada.whatsapp.profiles.legal.clients.LegalClientService.LegalClientInUseException;
import com.meada.whatsapp.profiles.legal.clients.LegalClientService.LegalClientNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Clientes do escritório (camada 7.2). TENANT + perfil 'legal' only. Sob /api/legal/clients.
 */
@RestController
public class LegalClientController {

    private final LegalClientService service;
    private final LegalProfileGuard profileGuard;

    public LegalClientController(LegalClientService service, LegalProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateClientRequest(
        @NotBlank @Size(max = 200) String name,
        String email, String phone, String document, UUID contactId, String notes) {}

    public record UpdateClientRequest(
        @Size(max = 200) String name,
        String email, String phone, String document, UUID contactId, String notes) {}

    @GetMapping("/api/legal/clients")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String search) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, search)));
    }

    @GetMapping("/api/legal/clients/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "client_not_found"));
    }

    @PostMapping("/api/legal/clients")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateClientRequest req) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        return ResponseEntity.status(201).body(service.create(companyId, user.userId(),
            req.name(), req.email(), req.phone(), req.document(), req.contactId(), req.notes()));
    }

    @PatchMapping("/api/legal/clients/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest req) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        try {
            // contactId no PATCH: presença do campo no JSON não é distinguível de null aqui;
            // tratamos contactId != null como "setar"; para desvincular use o endpoint futuro.
            boolean contactIdSet = req.contactId() != null;
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.name(),
                req.email(), req.phone(), req.document(), req.contactId(), contactIdSet, req.notes()));
        } catch (LegalClientNotFoundException e) {
            return error(404, "Not Found", "client_not_found");
        }
    }

    @DeleteMapping("/api/legal/clients/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try { companyId = profileGuard.requireLegal(user); }
        catch (WrongProfileException e) { return error(403, "Forbidden", "forbidden_wrong_profile"); }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (LegalClientNotFoundException e) {
            return error(404, "Not Found", "client_not_found");
        } catch (LegalClientInUseException e) {
            return error(409, "Conflict", "client_in_use");
        }
    }
}
