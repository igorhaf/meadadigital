package com.meada.whatsapp.admin.companies;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /admin/companies — lista todas as empresas. SUPER-ADMIN ONLY.
 *
 * <p>Autorização: check manual de role no método (não Spring Security / @PreAuthorize —
 * decisão arquitetural da camada 4). O JwtAuthenticationFilter já autenticou e populou o
 * {@code authenticatedUser}; aqui decidimos se o papel PODE ver isto.
 *
 * <p>403 {@code forbidden_not_super_admin}: erro de AUTORIZAÇÃO (tenant-admin tentando um
 * endpoint super-admin), DISTINTO do 403 {@code user_not_provisioned} do filtro (que é
 * falta de provisão). Ver DEVELOPMENT.md seção 4.1, "Divisão 401 vs 403". O shape do
 * corpo ({error, reason}) é o mesmo que o filtro escreve, para o frontend tratar o erro
 * com lógica única (apiFetch lê .reason independente da fonte).
 */
@RestController
public class CompanyAdminController {

    private final CompanyAdminRepository repository;

    public CompanyAdminController(CompanyAdminRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/admin/companies")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        if (user.role() != AdminRole.SUPER_ADMIN) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
        }
        return ResponseEntity.ok(repository.findAll());
    }
}
