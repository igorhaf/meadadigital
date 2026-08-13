package com.meada.admin.companies;

import com.meada.admin.security.AdminRole;
import com.meada.admin.security.AuthenticatedUser;
import com.meada.admin.security.JwtAuthenticationFilter;
import com.meada.common.audit.AuditLogger;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints de empresas do painel super-admin. SUPER-ADMIN ONLY (todos).
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
 * <p>Erros de validação de corpo (@Valid) são 400 com shape ValidationErrorResponse
 * (GlobalExceptionHandler), distinto do {error, reason} — o frontend lê o zod client-side
 * como 1ª barreira; o 400 do backend é defensivo.
 *
 * <p>Erros de NEGÓCIO deste controller (404 company/note não encontrada, 409 conflito de
 * status ou slug duplicado) são tratados localmente e devolvem {error, reason} — não são
 * sistêmicos, não pertencem ao GlobalExceptionHandler, e o shape casa com o 403, mantendo
 * o tratamento de erro do frontend unificado.
 */
@RestController
public class CompanyAdminController {

    private final CompanyAdminRepository repository;
    private final CompanyAdminService service;
    private final AuditLogger auditLogger;
    private final SupabaseAdminClient supabaseAdmin;

    public CompanyAdminController(CompanyAdminRepository repository, CompanyAdminService service,
                                  AuditLogger auditLogger, SupabaseAdminClient supabaseAdmin) {
        this.repository = repository;
        this.service = service;
        this.auditLogger = auditLogger;
        this.supabaseAdmin = supabaseAdmin;
    }

    /** Resposta padronizada {error, reason} (mesmo shape do filtro e do 403). */
    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    private static boolean notSuperAdmin(AuthenticatedUser user) {
        return user.role() != AdminRole.SUPER_ADMIN;
    }

    private static ResponseEntity<Object> forbidden() {
        return error(403, "Forbidden", "forbidden_not_super_admin");
    }

    // ---- GET lista (filtros + paginação) ------------------------------------

    /**
     * Lista empresas com filtros opcionais (status, q em name/slug ilike, createdAfter ISO)
     * e paginação (page 0-based default 0, pageSize default 20). Devolve
     * {items, total, page, pageSize}.
     */
    @GetMapping("/admin/companies")
    public ResponseEntity<Object> list(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(name = "created_after", required = false) String createdAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        Instant createdAfterTs = null;
        if (createdAfter != null && !createdAfter.isBlank()) {
            try {
                createdAfterTs = Instant.parse(createdAfter);
            } catch (java.time.format.DateTimeParseException e) {
                return error(400, "Bad Request", "invalid_created_after");
            }
        }
        int safePage = Math.max(0, page);
        int safePageSize = pageSize < 1 ? 20 : Math.min(pageSize, 200);
        return ResponseEntity.ok(service.list(status, q, createdAfterTs, safePage, safePageSize));
    }

    // ---- GET detalhe ---------------------------------------------------------

    @GetMapping("/admin/companies/{id}")
    public ResponseEntity<Object> detail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(service.getDetail(id));
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        }
    }

    /**
     * Email determinístico do tenant-admin da empresa (meada_{slug}_{token}@meadadigital.com), pro
     * root saber/copiar o login. Endpoint dedicado pra NÃO mudar a forma do GET detail (que o
     * frontend já consome). "" se a empresa ainda não tem admin.
     */
    @GetMapping("/admin/companies/{id}/admin-email")
    public ResponseEntity<Object> adminEmail(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        String email = repository.findOwnerEmail(id);
        return ResponseEntity.ok(Map.of("adminEmail", email == null ? "" : email));
    }

    // ---- POST cria (4.2 — inalterado) ---------------------------------------

    /**
     * Cria uma empresa. 201 com o CompanyResponse criado. Slug duplicado → 409
     * slug_already_exists.
     */
    @PostMapping("/admin/companies")
    public ResponseEntity<Object> create(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @Valid @RequestBody CreateCompanyRequest request) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        // O slug do tenant É o subdomínio dele: não pode colidir com um subdomínio de nicho
        // reservado (camada de roteamento de domínios). 409 distinto do slug_already_exists.
        if (com.meada.profiles.ProfileType.isReservedSubdomain(request.slug())) {
            return error(409, "Conflict", "slug_reserved_niche");
        }
        try {
            CompanyResponse created = repository.insert(
                request.name(), request.slug(), request.paletteId());
            auditLogger.log(created.id(), user.userId(), "created", "company", created.id(),
                Map.of("name", created.name(), "slug", created.slug(),
                       "paletteId", created.paletteId()));
            // Provisiona o tenant-admin com o email DETERMINÍSTICO meada_{slug}_{token}@meadadigital.com.
            // Best-effort: se a Admin API não está configurada (dev sem service_role), a empresa fica
            // criada e o admin é provisionado depois (seed/migração); não falha a criação.
            String adminEmail = provisionTenantAdmin(created.id(), created.slug());
            Object body = adminEmail == null
                ? created
                : Map.of("company", created, "adminEmail", adminEmail);
            return ResponseEntity.status(201).body(body);
        } catch (DuplicateKeyException e) {
            return error(409, "Conflict", "slug_already_exists");
        }
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Gera a senha do tenant-admin provisionado. Por padrão é ALEATÓRIA e forte (24 bytes
     * base64url, ~32 chars) — NUNCA volta no corpo da resposta nem em log; o admin recebe acesso
     * pelo fluxo de "esqueci a senha" do Supabase (link mágico).
     *
     * <p>Override só pra DEV: se {@code MEADA_DEV_ADMIN_PASSWORD} (env) estiver setado, usa esse
     * valor — assim o dev local tem uma senha conhecida sem cravar segredo no código. Em prod a
     * env não existe → senha aleatória. (Antes havia uma constante hardcoded no fonte versionado:
     * todo admin novo nascia com a MESMA senha conhecida — corrigido.)
     */
    private static String generateAdminPassword() {
        String dev = System.getenv("MEADA_DEV_ADMIN_PASSWORD");
        if (dev != null && !dev.isBlank()) {
            return dev;
        }
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Cria o tenant-admin de uma empresa nova no Auth + public.users com o email
     * {@code meada_{slug}_{admin_token}@meadadigital.com}. Devolve o email criado, ou null se a
     * Admin API está off ou o provisionamento falhou (não propaga — a empresa já foi criada).
     * A senha gerada NÃO é retornada (acesso via reset de senha do Supabase).
     */
    private String provisionTenantAdmin(UUID companyId, String slug) {
        if (!supabaseAdmin.enabled()) {
            return null;
        }
        try {
            String token = repository.findAdminToken(companyId);
            if (token == null) {
                return null;
            }
            String email = "meada_" + slug + "_" + token + "@meadadigital.com";
            String userId = supabaseAdmin.createUser(email, generateAdminPassword());
            repository.insertTenantAdmin(java.util.UUID.fromString(userId), companyId, email);
            auditLogger.log(companyId, null, "tenant_admin_provisioned", "user", companyId,
                Map.of("email", email));
            return email;
        } catch (RuntimeException e) {
            // não derruba a criação da empresa; o admin pode ser provisionado depois.
            return null;
        }
    }

    // ---- PATCH edita ---------------------------------------------------------

    @PatchMapping("/admin/companies/{id}")
    public ResponseEntity<Object> update(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        // profileId é opcional no PATCH; quando presente, tem de ser um perfil válido (camada 7.0).
        if (request.profileId() != null && !request.profileId().isBlank()
                && com.meada.profiles.ProfileType.fromId(request.profileId()).isEmpty()) {
            return error(400, "Bad Request", "invalid_profile_id");
        }
        // slug não pode colidir com subdomínio de nicho reservado (igual ao POST).
        if (com.meada.profiles.ProfileType.isReservedSubdomain(request.slug())) {
            return error(409, "Conflict", "slug_reserved_niche");
        }
        try {
            service.update(id, request, user.userId());
            return ResponseEntity.ok(service.getDetail(id));
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        } catch (DuplicateKeyException e) {
            return error(409, "Conflict", "slug_already_exists");
        }
    }

    // ---- POST suspende / reativa --------------------------------------------

    @PostMapping("/admin/companies/{id}/suspend")
    public ResponseEntity<Object> suspend(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestBody(required = false) SuspendCompanyRequest request) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        String reason = request == null ? null : request.reason();
        try {
            service.suspend(id, reason, user.userId());
            return ResponseEntity.noContent().build();
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        } catch (CompanyStatusConflictException e) {
            return error(409, "Conflict", e.reason());
        }
    }

    @PostMapping("/admin/companies/{id}/reactivate")
    public ResponseEntity<Object> reactivate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            service.reactivate(id, user.userId());
            return ResponseEntity.noContent().build();
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        } catch (CompanyStatusConflictException e) {
            return error(409, "Conflict", e.reason());
        }
    }

    // ---- POST entrar como empresa (impersonation) ----------------------------

    /**
     * "Entrar no admin desta empresa" (super-admin only): gera um magic link (token de uso
     * único) para o usuário-admin owner da empresa, que o frontend troca por uma sessão
     * Supabase real em /auth/confirm. Auditado (impersonated). 503 se a Admin API não está
     * configurada (sem service_role key); 409 se a empresa não tem admin elegível.
     */
    @PostMapping("/admin/companies/{id}/impersonate")
    public ResponseEntity<Object> impersonate(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        if (!supabaseAdmin.enabled()) {
            return error(503, "Service Unavailable", "impersonation_unavailable");
        }
        String email = repository.findOwnerEmail(id);
        if (email == null) {
            return error(409, "Conflict", "company_has_no_admin");
        }
        String tokenHash;
        try {
            tokenHash = supabaseAdmin.generateMagicLinkTokenHash(email);
        } catch (RuntimeException e) {
            return error(502, "Bad Gateway", "impersonation_link_failed");
        }
        auditLogger.log(id, user.userId(), "impersonated", "company", id, Map.of("as_email", email));
        // slug → o frontend abre o admin do tenant no SUBDOMÍNIO dele ({slug}.dominio), domínio
        // distinto do root → cookie de sessão separado (não derruba a sessão do super-admin).
        String slug = repository.findSlug(id);
        return ResponseEntity.ok(Map.of("tokenHash", tokenHash, "email", email, "slug", slug == null ? "" : slug));
    }

    // ---- DELETE (hard delete) ------------------------------------------------

    @DeleteMapping("/admin/companies/{id}")
    public ResponseEntity<Object> delete(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            service.hardDelete(id, user.userId());
            return ResponseEntity.noContent().build();
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        }
    }

    // ---- notas internas (CRUD) -----------------------------------------------

    @GetMapping("/admin/companies/{id}/notes")
    public ResponseEntity<Object> listNotes(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            return ResponseEntity.ok(service.listNotes(id));
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        }
    }

    @PostMapping("/admin/companies/{id}/notes")
    public ResponseEntity<Object> createNote(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            AdminNoteDto created = service.createNote(id, request.content(), user.userId());
            return ResponseEntity.status(201).body(created);
        } catch (CompanyNotFoundException e) {
            return error(404, "Not Found", "company_not_found");
        }
    }

    @PatchMapping("/admin/companies/{id}/notes/{noteId}")
    public ResponseEntity<Object> updateNote(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID noteId,
            @Valid @RequestBody NoteRequest request) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            AdminNoteDto updated = service.updateNote(id, noteId, request.content(), user.userId());
            return ResponseEntity.ok(updated);
        } catch (NoteNotFoundException e) {
            return error(404, "Not Found", "note_not_found");
        }
    }

    @DeleteMapping("/admin/companies/{id}/notes/{noteId}")
    public ResponseEntity<Object> deleteNote(
            @RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
            @PathVariable UUID id,
            @PathVariable UUID noteId) {
        if (notSuperAdmin(user)) {
            return forbidden();
        }
        try {
            service.deleteNote(id, noteId, user.userId());
            return ResponseEntity.noContent().build();
        } catch (NoteNotFoundException e) {
            return error(404, "Not Found", "note_not_found");
        }
    }
}
