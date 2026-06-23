package com.meada.whatsapp.profiles.showcase;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.showcase.NicheShowcaseService.TooManyFeaturedException;
import com.meada.whatsapp.profiles.showcase.NicheShowcaseService.UnknownProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints ROOT da vitrine de nichos. Mesma authz dos demais super-admin (role == SUPER_ADMIN;
 * senão 403 forbidden_not_super_admin — espelha ProfileFeatureController/CompanyAdminController).
 *
 * <ul>
 *   <li>{@code GET /admin/niches/showcase} → grade: todos os nichos + featured + ordem.</li>
 *   <li>{@code PUT /admin/niches/showcase/{profileId}} {featured, displayOrder} → edita.</li>
 * </ul>
 */
@RestController
public class NicheShowcaseController {

    private final NicheShowcaseService service;

    public NicheShowcaseController(NicheShowcaseService service) {
        this.service = service;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    private static boolean notSuperAdmin(AuthenticatedUser user) {
        return user.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> forbidden() {
        return error(403, "Forbidden", "forbidden_not_super_admin");
    }

    public record SetRequest(boolean featured, int displayOrder) {}

    @GetMapping("/admin/niches/showcase")
    public ResponseEntity<Object> grid(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("niches", service.grid(), "maxFeatured", NicheShowcaseService.MAX_FEATURED));
    }

    @PutMapping("/admin/niches/showcase/{profileId}")
    public ResponseEntity<Object> set(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable String profileId,
            @RequestBody SetRequest req) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            service.set(profileId, req.featured(), req.displayOrder(), user.userId());
            return ResponseEntity.ok(Map.of("profileId", profileId, "featured", req.featured(),
                "displayOrder", req.displayOrder()));
        } catch (UnknownProfileException e) {
            return error(400, "Bad Request", "unknown_profile");
        } catch (TooManyFeaturedException e) {
            return error(409, "Conflict", "too_many_featured");
        }
    }
}
