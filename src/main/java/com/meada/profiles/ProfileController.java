package com.meada.profiles;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoints do catálogo de perfis (camada 7.0).
 *
 * <ul>
 *   <li>GET /admin/profiles — catálogo completo (super-admin only), alimenta o dropdown do root.
 *   <li>GET /admin/me/profile-match — valida se o usuário pode acessar um dado subdomínio
 *       (qualquer autenticado). Base da UX de redirect educado no login.
 * </ul>
 */
@RestController
public class ProfileController {

    private final JdbcTemplate jdbcTemplate;

    public ProfileController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static Map<String, Object> toDto(ProfileType p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.id());
        m.put("productName", p.productName());
        m.put("subdomain", p.subdomain());
        m.put("defaultPaletteId", p.defaultPaletteId());
        return m;
    }

    // -------------------------------------------------------------------------
    // GET /admin/profiles — catálogo (super-admin only)
    // -------------------------------------------------------------------------
    @GetMapping("/admin/profiles")
    public ResponseEntity<Object> profiles(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.SUPER_ADMIN) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
        }
        List<Map<String, Object>> items = ProfileType.allActive().stream()
            .map(ProfileController::toDto).toList();
        return ResponseEntity.ok(Map.of("items", items));
    }

    // -------------------------------------------------------------------------
    // GET /admin/me/profile-match?subdomain={sub} — validação user×subdomínio
    // -------------------------------------------------------------------------
    @GetMapping("/admin/me/profile-match")
    public ResponseEntity<Object> profileMatch(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam String subdomain) {
        // O subdomínio precisa mapear a um perfil conhecido (senão é entrada inválida).
        Optional<ProfileType> target = ProfileType.bySubdomain(subdomain);
        if (target.isEmpty()) {
            return ResponseEntity.status(400)
                .body(Map.of("error", "Bad Request", "reason", "unknown_subdomain"));
        }

        // Super-admin acessa QUALQUER subdomínio (visão global). Sempre match.
        if (user.role() == AdminRole.SUPER_ADMIN) {
            Map<String, Object> ok = new HashMap<>();
            ok.put("match", true);
            ok.put("productName", target.get().productName());
            return ResponseEntity.ok(ok);
        }

        // Tenant: lê o perfil da própria empresa e compara com o do subdomínio.
        UUID companyId = user.companyId();
        if (companyId == null) {
            // INVITEE / usuário sem empresa provisionada — sem perfil a casar.
            Map<String, Object> nope = new HashMap<>();
            nope.put("match", false);
            return ResponseEntity.ok(nope);
        }
        String companyProfileId = jdbcTemplate.query(
                "select profile_id from companies where id = ?",
                (rs, rn) -> rs.getString("profile_id"), companyId)
            .stream().findFirst().orElse(null);

        ProfileType companyProfile = companyProfileId == null
            ? null : ProfileType.fromId(companyProfileId).orElse(null);

        if (companyProfile != null && companyProfile == target.get()) {
            Map<String, Object> ok = new HashMap<>();
            ok.put("match", true);
            ok.put("productName", companyProfile.productName());
            return ResponseEntity.ok(ok);
        }

        // Mismatch: devolve para onde o usuário DEVERIA ir (subdomínio/produto do perfil dele).
        Map<String, Object> mismatch = new HashMap<>();
        mismatch.put("match", false);
        if (companyProfile != null) {
            mismatch.put("expectedSubdomain", companyProfile.subdomain());
            mismatch.put("expectedProductName", companyProfile.productName());
        }
        return ResponseEntity.ok(mismatch);
    }
}
