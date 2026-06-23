package com.meada.whatsapp.cms;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * CMS PÚBLICO (SM-N), multi-página. SEM auth (rotas fora da allowlist do JwtFilter). Serve a página
 * PUBLICADA (de site publicado) por slug/pageSlug/domínio-verificado; 404 senão. A view inclui o
 * tema e a navegação (páginas publicadas) pra o render montar o menu. Nunca expõe campos internos.
 */
@RestController
public class CmsPublicController {

    private final CmsService service;

    public CmsPublicController(CmsService service) {
        this.service = service;
    }

    /** Item de navegação público. */
    public record NavItem(String pageSlug, String title, boolean isHome) {}

    /** View pública: a página (title+blocks) + tema do site + nav (páginas publicadas). */
    public record PublicView(String title, JsonNode blocks, JsonNode theme, List<NavItem> nav) {}

    private PublicView view(UUID companyId, CmsPage page) {
        JsonNode theme = service.siteByCompany(companyId).map(CmsSite::theme).orElse(null);
        List<NavItem> nav = service.publishedNav(companyId).stream()
            .map(p -> new NavItem(p.pageSlug(), p.title(), p.isHome())).toList();
        return new PublicView(page.title(), page.blocks(), theme, nav);
    }

    private ResponseEntity<Object> ok(CmsPage page) {
        return ResponseEntity.ok(view(page.companyId(), page));
    }

    private static ResponseEntity<Object> notFound() {
        return ResponseEntity.status(404).body(Map.of("error", "Not Found", "reason", "page_not_found"));
    }

    @GetMapping("/public/cms/by-slug/{slug}")
    public ResponseEntity<Object> homeBySlug(@PathVariable String slug) {
        Optional<CmsPage> page = service.publishedHomeBySlug(slug);
        return page.map(this::ok).orElseGet(CmsPublicController::notFound);
    }

    @GetMapping("/public/cms/by-slug/{slug}/{pageSlug}")
    public ResponseEntity<Object> pageBySlug(@PathVariable String slug, @PathVariable String pageSlug) {
        Optional<CmsPage> page = service.publishedPageBySlug(slug, pageSlug);
        return page.map(this::ok).orElseGet(CmsPublicController::notFound);
    }

    @GetMapping("/public/cms/by-domain")
    public ResponseEntity<Object> byDomain(@RequestParam String host) {
        Optional<CmsPage> page = service.publishedHomeByDomain(host);
        return page.map(this::ok).orElseGet(CmsPublicController::notFound);
    }

    @GetMapping("/public/cms/by-domain/{pageSlug}")
    public ResponseEntity<Object> pageByDomain(@RequestParam String host, @PathVariable String pageSlug) {
        Optional<CmsPage> page = service.publishedPageByDomain(host, pageSlug);
        return page.map(this::ok).orElseGet(CmsPublicController::notFound);
    }

    /**
     * Endpoint do Caddy on-demand-TLS (SM-N): o Caddy consulta aqui antes de emitir um cert pra um
     * host. 200 = pode emitir (domínio VERIFICADO + publicado no cms_sites); 404 = não emite. Isso
     * impede que qualquer um aponte um domínio pro nosso IP e force emissão de cert. Sem auth (o
     * Caddy chama server-to-server). Ver docs/CMS.md (runbook).
     */
    @GetMapping("/public/cms/tls-allowed")
    public ResponseEntity<Void> tlsAllowed(@RequestParam String domain) {
        return service.domainAllowedForTls(domain)
            ? ResponseEntity.ok().build()
            : ResponseEntity.status(404).build();
    }

    /**
     * Resolução pública de {empresa}.meadadigital.com (roteamento de domínios): o middleware do
     * frontend consulta aqui pra decidir — empresa existe? tem CMS publicado (→ /p)? senão, qual o
     * subdomínio do nicho dela (→ login do nicho)? Sempre 200 (exists=false quando não há empresa
     * ativa com esse slug). SEM auth (rota /public/).
     */
    @GetMapping("/public/companies/resolve/{slug}")
    public ResponseEntity<Object> resolveCompany(@PathVariable String slug) {
        return service.resolvePublicCompany(slug)
            .<ResponseEntity<Object>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.ok(
                new CmsService.CompanyResolve(false, null, null, false)));
    }
}

