package com.meada.admin.plans;

import com.meada.admin.plans.PlanDtos.CreatePlanRequest;
import com.meada.admin.plans.PlanDtos.UpdatePlanRequest;
import com.meada.admin.plans.PlanService.PlanNotFoundException;
import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * CRUD de planos do painel super-admin (camada 6.8). SUPER-ADMIN ONLY.
 *
 * <p>Slug/name únicos → 409 plan_slug_exists. DELETE é SOFT (active=false). NÃO integra com
 * companies.plan_id (fase futura) — só entrega o catálogo isolado. Padrão {error, reason}.
 */
@RestController
public class AdminPlanController {

    private final PlanRepository repository;
    private final PlanService service;

    public AdminPlanController(PlanRepository repository, PlanService service) {
        this.repository = repository;
        this.service = service;
    }

    private static boolean notSuper(AuthenticatedUser u) {
        return u.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    private static ResponseEntity<Object> forbidden() {
        return error(403, "Forbidden", "forbidden_not_super_admin");
    }

    @GetMapping("/admin/plans")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (notSuper(user)) return forbidden();
        return ResponseEntity.ok(Map.of("items", repository.findAll()));
    }

    @GetMapping("/admin/plans/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) return forbidden();
        return repository.findById(id)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> error(404, "Not Found", "plan_not_found"));
    }

    @PostMapping("/admin/plans")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreatePlanRequest req) {
        if (notSuper(user)) return forbidden();
        try {
            return ResponseEntity.status(201).body(service.create(req, user.userId()));
        } catch (DuplicateKeyException e) {
            return error(409, "Conflict", "plan_slug_exists");
        }
    }

    @PatchMapping("/admin/plans/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePlanRequest req) {
        if (notSuper(user)) return forbidden();
        try {
            return ResponseEntity.ok(service.update(id, req, user.userId()));
        } catch (PlanNotFoundException e) {
            return error(404, "Not Found", "plan_not_found");
        } catch (DuplicateKeyException e) {
            return error(409, "Conflict", "plan_slug_exists");
        }
    }

    @DeleteMapping("/admin/plans/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuper(user)) return forbidden();
        try {
            service.softDelete(id, user.userId());
            return ResponseEntity.noContent().build();
        } catch (PlanNotFoundException e) {
            return error(404, "Not Found", "plan_not_found");
        }
    }
}
