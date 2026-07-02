package com.meada.profiles.academia.coupons;

import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.profiles.academia.AcademiaProfileGuard;
import com.meada.profiles.academia.AcademiaProfileGuard.WrongProfileException;
import com.meada.profiles.academia.coupons.AcademiaCouponService.CouponNotFoundException;
import com.meada.profiles.academia.coupons.AcademiaCouponService.CouponValidation;
import com.meada.profiles.academia.coupons.AcademiaCouponService.DuplicateCouponException;
import com.meada.profiles.academia.coupons.AcademiaCouponService.InvalidCouponException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
 * Cupons de desconto do tenant academia (camada 7.7), espelho do SushiCouponController. TENANT +
 * perfil 'academia' only, sob {@code /api/academia/**}. CRUD do catálogo + endpoint de validação
 * ({@code POST /api/academia/coupons/validate}) que devolve o desconto aplicável a um subtotal.
 */
@RestController
public class AcademiaCouponController {

    private final AcademiaCouponService service;
    private final AcademiaProfileGuard profileGuard;

    public AcademiaCouponController(AcademiaCouponService service, AcademiaProfileGuard profileGuard) {
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
        Integer minCents,
        Integer maxUses,
        LocalDate validUntil,
        Boolean active) {}

    public record UpdateRequest(
        @Size(max = 40) String code,
        String kind,
        Integer value,
        Integer minCents,
        Integer maxUses,
        Boolean clearMaxUses,
        LocalDate validUntil,
        Boolean clearValidUntil,
        Boolean active) {}

    public record ToggleRequest(boolean active) {}

    public record ValidateRequest(
        @NotBlank String code,
        @NotNull @PositiveOrZero Integer subtotalCents) {}

    @GetMapping("/api/academia/coupons")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        return ResponseEntity.ok(Map.of("items", service.list(companyId)));
    }

    @GetMapping("/api/academia/coupons/{id}")
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
            .orElseGet(() -> error(404, "Not Found", "coupon_not_found"));
    }

    @PostMapping("/api/academia/coupons")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.status(201).body(service.create(companyId, user.userId(), req.code(),
                req.kind(), req.value(), req.minCents(), req.maxUses(), req.validUntil(), req.active()));
        } catch (InvalidCouponException e) {
            return error(400, "Bad Request", "invalid_coupon");
        } catch (DuplicateCouponException e) {
            return error(409, "Conflict", "duplicate_coupon");
        }
    }

    @PatchMapping("/api/academia/coupons/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody UpdateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        boolean maxUsesProvided = req.maxUses() != null || Boolean.TRUE.equals(req.clearMaxUses());
        Integer maxUses = Boolean.TRUE.equals(req.clearMaxUses()) ? null : req.maxUses();
        boolean validUntilProvided = req.validUntil() != null || Boolean.TRUE.equals(req.clearValidUntil());
        LocalDate validUntil = Boolean.TRUE.equals(req.clearValidUntil()) ? null : req.validUntil();
        try {
            return ResponseEntity.ok(service.update(companyId, user.userId(), id, req.code(), req.kind(),
                req.value(), req.minCents(), maxUses, maxUsesProvided, validUntil, validUntilProvided,
                req.active()));
        } catch (InvalidCouponException e) {
            return error(400, "Bad Request", "invalid_coupon");
        } catch (CouponNotFoundException e) {
            return error(404, "Not Found", "coupon_not_found");
        } catch (DuplicateCouponException e) {
            return error(409, "Conflict", "duplicate_coupon");
        }
    }

    @PatchMapping("/api/academia/coupons/{id}/toggle")
    public ResponseEntity<Object> toggle(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id, @RequestBody ToggleRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        try {
            return ResponseEntity.ok(service.toggle(companyId, user.userId(), id, req.active()));
        } catch (CouponNotFoundException e) {
            return error(404, "Not Found", "coupon_not_found");
        }
    }

    @DeleteMapping("/api/academia/coupons/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
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

    @PostMapping("/api/academia/coupons/validate")
    public ResponseEntity<Object> validate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody ValidateRequest req) {
        UUID companyId;
        try {
            companyId = profileGuard.requireAcademia(user);
        } catch (WrongProfileException e) {
            return error(403, "Forbidden", "forbidden_wrong_profile");
        }
        CouponValidation result = service.validate(companyId, req.code(), req.subtotalCents());
        return ResponseEntity.ok(result);
    }
}
