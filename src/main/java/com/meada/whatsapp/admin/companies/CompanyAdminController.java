package com.meada.whatsapp.admin.companies;

import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoints de empresas do painel super-admin. SUPER-ADMIN ONLY (ambos).
 *
 * <p>Autorização: check manual de role no método (não Spring Security / @PreAuthorize —
 * decisão arquitetural da camada 4). O JwtAuthenticationFilter já autenticou e populou o
 * {@code authenticatedUser}; aqui decidimos se o papel PODE agir.
 *
 * <p>403 {@code forbidden_not_super_admin}: erro de AUTORIZAÇÃO (tenant-admin tentando um
 * endpoint super-admin), DISTINTO do 403 {@code user_not_provisioned} do filtro (que é
 * falta de provisão). Ver DEVELOPMENT.md seção 4.1, "Divisão 401 vs 403". O shape do
 * corpo ({error, reason}) é o mesmo que o filtro escreve, para o frontend tratar o erro
 * com lógica única (apiFetch lê .reason independente da fonte).
 *
 * <p>Erros de validação de corpo (@Valid no POST) são 400 com shape ValidationErrorResponse
 * (GlobalExceptionHandler), distinto do {error, reason} — o frontend lê o zod client-side
 * como 1ª barreira; o 400 do backend é defensivo.
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

    /**
     * Cria uma empresa. 201 com o CompanyResponse criado. Slug duplicado → 409
     * slug_already_exists (try/catch local da DuplicateKeyException: é regra de negócio
     * deste endpoint, não erro sistêmico — não pertence ao GlobalExceptionHandler, e o
     * shape {error, reason} casa com o 403 acima, mantendo o tratamento de erro do
     * frontend unificado).
     */
    @PostMapping("/admin/companies")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateCompanyRequest request) {
        if (user.role() != AdminRole.SUPER_ADMIN) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden", "reason", "forbidden_not_super_admin"));
        }
        try {
            CompanyResponse created = repository.insert(
                request.name(), request.slug(), request.paletteId());
            return ResponseEntity.status(201).body(created);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409)
                .body(Map.of("error", "Conflict", "reason", "slug_already_exists"));
        }
    }
}
