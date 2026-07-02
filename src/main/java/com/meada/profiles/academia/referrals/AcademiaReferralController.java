package com.meada.profiles.academia.referrals;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.profiles.academia.referrals.AcademiaReferralService.ReferralNotFoundException;
import com.meada.profiles.academia.referrals.AcademiaReferralService.ReferralNotPendingException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
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
 * Programa de indicação do tenant academia (camada 7.7). TENANT + perfil 'academia' only.
 * POST gera um código único; GET lista; PATCH /convert marca uma indicação pendente como convertida.
 */
@RestController
public class AcademiaReferralController {

    private final AcademiaReferralService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaReferralController(AcademiaReferralService service, AcademiaProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateReferralRequest(
        UUID referrerContactId,
        @NotBlank @Size(max = 200) String referredName,
        @Size(max = 40) String referredPhone,
        @Min(1) @Max(100) Integer rewardPercent) {}

    @GetMapping("/api/academia/referrals")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId, status)));
    }

    @GetMapping("/api/academia/referrals/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return service.get(companyId, id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "referral_not_found"));
    }

    @PostMapping("/api/academia/referrals")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateReferralRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        AcademiaReferral created = service.create(companyId, user.userId(), req.referrerContactId(),
            req.referredName(), req.referredPhone(), req.rewardPercent());
        return ResponseEntity.status(201).body(created);
    }

    @PatchMapping("/api/academia/referrals/{id}/convert")
    public ResponseEntity<Object> convert(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.convert(companyId, user.userId(), id));
        } catch (ReferralNotFoundException e) {
            return error(404, "Not Found", "referral_not_found");
        } catch (ReferralNotPendingException e) {
            return error(409, "Conflict", "referral_not_pending");
        }
    }
}
