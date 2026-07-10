package com.meada.profiles.atelie.coupons;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.atelie.AtelieProfileGuard;
import com.meada.profiles.atelie.AtelieProfileGuard.WrongProfileException;
import com.meada.common.coupons.CouponServiceBase.CouponNotFoundException;
import com.meada.common.coupons.CouponServiceBase.DuplicateCouponException;
import com.meada.common.coupons.CouponServiceBase.InvalidCouponException;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Cupons de desconto do tenant atelie (onda 2, backlog #13 — clone do motor adega). TENANT +
 * perfil 'atelie' only. CRUD do catálogo de cupons; a aplicação acontece na proposta (painel),
 * via {@code PATCH /api/atelie/proposals/{id}/coupon}.
 */
@RestController
public class AtelieCouponController {

    private final AtelieCouponService service;
    private final AtelieProfileGuard profileGuard;

    public AtelieCouponController(AtelieCouponService service, AtelieProfileGuard profileGuard) {
        this.service = service;
        this.profileGuard = profileGuard;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    public record CreateRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank String kind,
        @NotNull Integer value,
        Integer minOrderCents,
        Integer maxUses,
        LocalDate validUntil,
        Boolean active) {}

    public record UpdateRequest(
        @Size(max = 40) String code,
        String kind,
        Integer value,
        Integer minOrderCents,
        Integer maxUses,
        Boolean clearMaxUses,
        LocalDate validUntil,
        Boolean clearValidUntil,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    @GetMapping("/api/atelie/coupons")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId)));
    }

    @PostMapping("/api/atelie/coupons")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.code(),
                req.kind(), req.value(), req.minOrderCents(), req.maxUses(), req.validUntil(), req.active()));
        } catch (InvalidCouponException e) {
            return error(400, "Bad Request", "invalid_coupon");
        } catch (DuplicateCouponException e) {
            return error(409, "Conflict", "duplicate_coupon");
        }
    }

    @PatchMapping("/api/atelie/coupons/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean maxUsesProvided = req.maxUses() != null || Boolean.TRUE.equals(req.clearMaxUses());
        Integer maxUses = Boolean.TRUE.equals(req.clearMaxUses()) ? null : req.maxUses();
        boolean validUntilProvided = req.validUntil() != null || Boolean.TRUE.equals(req.clearValidUntil());
        LocalDate validUntil = Boolean.TRUE.equals(req.clearValidUntil()) ? null : req.validUntil();
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.code(), req.kind(),
                req.value(), req.minOrderCents(), maxUses, maxUsesProvided, validUntil, validUntilProvided,
                req.active()));
        } catch (InvalidCouponException e) {
            return error(400, "Bad Request", "invalid_coupon");
        } catch (CouponNotFoundException e) {
            return error(404, "Not Found", "coupon_not_found");
        } catch (DuplicateCouponException e) {
            return error(409, "Conflict", "duplicate_coupon");
        }
    }

    @PatchMapping("/api/atelie/coupons/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (CouponNotFoundException e) {
            return error(404, "Not Found", "coupon_not_found");
        }
    }

    @DeleteMapping("/api/atelie/coupons/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAtelie(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            service.delete(companyId, user.userId(), id);
            return ResponseEntity.noContent().build();
        } catch (CouponNotFoundException e) {
            return error(404, "Not Found", "coupon_not_found");
        }
    }
}
