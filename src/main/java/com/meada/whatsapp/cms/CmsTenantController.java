package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.meada.whatsapp.admin.security.AdminRole;
import com.meada.whatsapp.admin.security.AuthenticatedUser;
import com.meada.whatsapp.admin.security.JwtAuthenticationFilter;
import com.meada.whatsapp.cms.CmsService.DomainTakenException;
import com.meada.whatsapp.cms.CmsService.InvalidBlocksException;
import com.meada.whatsapp.cms.CmsService.InvalidDomainException;
import com.meada.whatsapp.cms.CmsService.InvalidPageSlugException;
import com.meada.whatsapp.cms.CmsService.NoDomainException;
import com.meada.whatsapp.cms.CmsService.PageNotFoundException;
import com.meada.whatsapp.cms.CmsService.PageSlugTakenException;
import com.meada.whatsapp.cms.CmsService.TooManyPagesException;
import com.meada.whatsapp.profiles.PlatformCompany;
import com.meada.whatsapp.profiles.ProfileFeature;
import com.meada.whatsapp.profiles.features.ProfileFeatureGuard;
import com.meada.whatsapp.profiles.features.ProfileFeatureGuard.FeatureDisabledException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * CMS do TENANT (SM-N) E do ROOT (CMS-root, B1), multi-página. Para o TENANT, tudo atrás do gate
 * {@link ProfileFeatureGuard#requireFeature} (403 feature_disabled se o nicho não tem CMS). Para o
 * SUPER-ADMIN (que NÃO tem company), o CMS é EMBUTIDO — sempre ligado, sem passar pela grade de
 * feature flags — e opera sobre a COMPANY-ÂNCORA da plataforma ({@link PlatformCompany}, o "Meada"
 * do root, migration 44). Gerencia o SITE (publicar, tema, domínio + verificação de posse) e as
 * PÁGINAS (CRUD, home).
 */
@RestController
public class CmsTenantController {

    private final CmsService service;
    private final ProfileFeatureGuard featureGuard;
    private final PlatformCompany platformCompany;

    public CmsTenantController(CmsService service, ProfileFeatureGuard featureGuard,
                               PlatformCompany platformCompany) {
        this.service = service;
        this.featureGuard = featureGuard;
        this.platformCompany = platformCompany;
    }

    private static ResponseEntity<Object> error(int status, String error, String reason) {
        return ResponseEntity.status(status).body(Map.of("error", error, "reason", reason));
    }

    /**
     * Resolve a company do CMS + autoriza. SUPER-ADMIN (sem company) → company-âncora da plataforma,
     * CMS embutido (sem checar feature flag). TENANT → sua company, atrás do
     * {@code requireFeature(CMS)} (403 feature_disabled se o nicho não tem CMS).
     */
    private UUID gate(AuthenticatedUser user) {
        if (user.role() == AdminRole.SUPER_ADMIN) {
            return platformCompany.companyId();
        }
        return featureGuard.requireFeature(user, ProfileFeature.CMS);
    }

    public record PublishRequest(boolean published) {}
    public record ThemeRequest(JsonNode theme) {}
    public record DomainRequest(String domain) {}
    public record CreatePageRequest(String pageSlug, String title) {}
    public record SavePageRequest(String title, JsonNode blocks, Boolean published) {}

    // ---- SITE ----------------------------------------------------------------

    @GetMapping("/api/cms/site")
    public ResponseEntity<Object> getSite(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        return ResponseEntity.ok(Map.of("site", service.getOrCreateSite(companyId), "pages", service.listPages(companyId)));
    }

    @PutMapping("/api/cms/site/publish")
    public ResponseEntity<Object> publish(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                          @RequestBody PublishRequest req) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        return ResponseEntity.ok(service.setPublished(companyId, req.published()));
    }

    @PutMapping("/api/cms/site/theme")
    public ResponseEntity<Object> theme(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                        @RequestBody ThemeRequest req) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        return ResponseEntity.ok(service.setTheme(companyId, req.theme()));
    }

    @PutMapping("/api/cms/site/domain")
    public ResponseEntity<Object> domain(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                         @RequestBody DomainRequest req) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            return ResponseEntity.ok(service.setDomain(companyId, req.domain()));
        } catch (InvalidDomainException e) {
            return error(400, "Bad Request", "invalid_domain");
        } catch (DomainTakenException e) {
            return error(409, "Conflict", "domain_taken");
        }
    }

    /** Gera/retorna o token de verificação (pra o tenant publicar no TXT). */
    @PostMapping("/api/cms/site/verify/start")
    public ResponseEntity<Object> verifyStart(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            return ResponseEntity.ok(service.startDomainVerification(companyId));
        } catch (NoDomainException e) {
            return error(400, "Bad Request", "no_domain");
        }
    }

    /** Consulta o TXT e marca verified se o token bater. */
    @PostMapping("/api/cms/site/verify")
    public ResponseEntity<Object> verify(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            return ResponseEntity.ok(service.verifyDomain(companyId));
        } catch (NoDomainException e) {
            return error(400, "Bad Request", "no_domain");
        }
    }

    // ---- PÁGINAS -------------------------------------------------------------

    @PostMapping("/api/cms/pages")
    public ResponseEntity<Object> createPage(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                             @RequestBody CreatePageRequest req) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            return ResponseEntity.status(201).body(service.createPage(companyId, req.pageSlug(), req.title()));
        } catch (InvalidPageSlugException e) {
            return error(400, "Bad Request", "invalid_page_slug");
        } catch (PageSlugTakenException e) {
            return error(409, "Conflict", "page_slug_taken");
        } catch (TooManyPagesException e) {
            return error(400, "Bad Request", "too_many_pages");
        }
    }

    @PutMapping("/api/cms/pages/{id}")
    public ResponseEntity<Object> savePage(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                           @PathVariable UUID id, @RequestBody SavePageRequest req) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            return ResponseEntity.ok(service.savePageContent(companyId, id, req.title(), req.blocks(), req.published()));
        } catch (InvalidBlocksException e) {
            return error(400, "Bad Request", "invalid_blocks");
        } catch (PageNotFoundException e) {
            return error(404, "Not Found", "page_not_found");
        }
    }

    @PutMapping("/api/cms/pages/{id}/home")
    public ResponseEntity<Object> setHome(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                          @PathVariable UUID id) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            return ResponseEntity.ok(service.setHome(companyId, id));
        } catch (PageNotFoundException e) {
            return error(404, "Not Found", "page_not_found");
        }
    }

    @DeleteMapping("/api/cms/pages/{id}")
    public ResponseEntity<Object> deletePage(@RequestAttribute(JwtAuthenticationFilter.AUTH_USER_ATTRIBUTE) AuthenticatedUser user,
                                             @PathVariable UUID id) {
        UUID companyId;
        try { companyId = gate(user); } catch (FeatureDisabledException e) { return error(403, "Forbidden", "feature_disabled"); }
        try {
            service.deletePage(companyId, id);
            return ResponseEntity.noContent().build();
        } catch (PageNotFoundException e) {
            return error(404, "Not Found", "page_not_found");
        }
    }
}
