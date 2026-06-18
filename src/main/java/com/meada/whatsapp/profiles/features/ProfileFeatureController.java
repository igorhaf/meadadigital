package com.meada.whatsapp.profiles.features;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.profiles.features.ProfileFeatureService.UnknownFeatureException;
import com.meada.whatsapp.profiles.features.ProfileFeatureService.UnknownProfileException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints ROOT das feature flags por nicho (camada 9.0). Plataforma — atrás da MESMA authz dos
 * demais endpoints super-admin: {@code user.role() == SUPER_ADMIN}; senão 403
 * forbidden_not_super_admin (espelha CompanyAdminController/GlobalMetricsController).
 *
 * <ul>
 *   <li>{@code GET /admin/profile-features} → a grade computada (features × nichos).</li>
 *   <li>{@code PUT /admin/profile-features/{profileId}/{featureKey}} → liga/desliga a flag.</li>
 * </ul>
 */
@RestController
public class ProfileFeatureController {

    private final ProfileFeatureService service;

    public ProfileFeatureController(ProfileFeatureService service) {
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

    public record ToggleRequest(boolean enabled) {}

    @GetMapping("/admin/profile-features")
    public ResponseEntity<Object> grid(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        return ResponseEntity.ok(service.grid());
    }

    @PutMapping("/admin/profile-features/{profileId}/{featureKey}")
    public ResponseEntity<Object> setFlag(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable String profileId,
            @PathVariable String featureKey,
            @RequestBody ToggleRequest req) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            service.setFlag(profileId, featureKey, req.enabled(), user.userId());
            return ResponseEntity.ok(Map.of("profileId", profileId, "featureKey", featureKey, "enabled", req.enabled()));
        } catch (UnknownProfileException e) {
            return error(400, "Bad Request", "unknown_profile");
        } catch (UnknownFeatureException e) {
            return error(400, "Bad Request", "unknown_feature");
        }
    }
}
