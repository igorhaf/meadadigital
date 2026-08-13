package com.meada.admin.dashboard;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Hub do super-admin (camada 6.0) — KPIs agregados da plataforma. SUPER-ADMIN ONLY:
 * check manual de role (padrão da camada 4, igual CompanyAdminController). 403
 * forbidden_not_super_admin para tenant-admin.
 */
@RestController
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping("/admin/dashboard/overview")
    public ResponseEntity<Object> overview(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.SUPER_ADMIN) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
        }
        return ResponseEntity.ok(service.getOverview());
    }
}
