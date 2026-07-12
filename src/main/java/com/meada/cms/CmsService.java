package com.meada.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Regras do CMS multi-página (SM-N). O tenant gerencia o SITE (domínio + verificação + tema +
 * publicação) e as PÁGINAS (CRUD, home, blocos); o público lê páginas publicadas por slug, por
 * pageSlug, ou por domínio VERIFICADO.
 *
 * <p>Validação de blocks: app-level, normaliza {@code {id, type, props}} (type ∈ CmsBlockType).
 * Domínio: hostname, lowercase, único, não-Meada. Posse: TXT {@code _meada-verify=<token>} via
 * {@link DnsTxtResolver}.
 */
@Service
public class CmsService {

    private static final Pattern DOMAIN = Pattern.compile("^(?=.{1,253}$)([a-z0-9](-?[a-z0-9])*\\.)+[a-z]{2,}$");
    private static final Pattern PAGE_SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    private static final String VERIFY_PREFIX = "_meada-verify=";
    private static final int MAX_BLOCKS = 50;   // total de blocos-folha na árvore
    private static final int MAX_ROWS = 30;
    private static final int MAX_COLS_ROW = 6;
    private static final int MAX_PAGES = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CmsSiteRepository siteRepository;
    private final CmsPageRepository pageRepository;
    private final DnsTxtResolver dnsResolver;
    private final ObjectMapper objectMapper;

    public CmsService(CmsSiteRepository siteRepository, CmsPageRepository pageRepository,
                      DnsTxtResolver dnsResolver, ObjectMapper objectMapper) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.dnsResolver = dnsResolver;
        this.objectMapper = objectMapper;
    }

    public static class InvalidBlocksException extends RuntimeException {}
    public static class InvalidDomainException extends RuntimeException {}
    public static class DomainTakenException extends RuntimeException {}
    public static class InvalidPageSlugException extends RuntimeException {}
    public static class PageSlugTakenException extends RuntimeException {}
    public static class PageNotFoundException extends RuntimeException {}
    public static class TooManyPagesException extends RuntimeException {}
    public static class NoDomainException extends RuntimeException {}

    // ---- SITE ----------------------------------------------------------------

    @Transactional
    public CmsSite getOrCreateSite(UUID companyId) {
        siteRepository.ensureExists(companyId);
        return siteRepository.findByCompany(companyId).orElseThrow();
    }

    @Transactional
    public CmsSite setPublished(UUID companyId, boolean published) {
        return siteRepository.setPublished(companyId, published);
    }

    @Transactional
    public CmsSite setTheme(UUID companyId, JsonNode theme) {
        String json;
        try {
            json = objectMapper.writeValueAsString(theme == null || !theme.isObject() ? objectMapper.createObjectNode() : theme);
        } catch (Exception e) {
            json = "{}";
        }
        return siteRepository.setTheme(companyId, json);
    }

    @Transactional
    public CmsSite setDomain(UUID companyId, String rawDomain) {
        String domain = rawDomain == null ? null : rawDomain.trim().toLowerCase();
        if (domain != null && domain.isEmpty()) {
            domain = null;
        }
        if (domain != null) {
            // Bloqueio por igualdade OU sufixo COM PONTO: endsWith("meadadigital.com") sem o
            // separador de label rejeitava domínios legítimos como 'minhameadadigital.com'.
            boolean meadaDomain = domain.equals("meadadigital.com") || domain.endsWith(".meadadigital.com")
                || domain.equals("meadadigital.local") || domain.endsWith(".meadadigital.local");
            if (!DOMAIN.matcher(domain).matches() || meadaDomain) {
                throw new InvalidDomainException();
            }
        }
        try {
            return siteRepository.setDomain(companyId, domain);
        } catch (DataIntegrityViolationException e) {
            throw new DomainTakenException();
        }
    }

    /** Gera (ou retorna) o token de verificação a publicar no TXT. Exige domínio setado. */
    @Transactional
    public CmsSite startDomainVerification(UUID companyId) {
        CmsSite site = getOrCreateSite(companyId);
        if (site.domain() == null) {
            throw new NoDomainException();
        }
        String token = site.verifyToken();
        if (token == null || token.isBlank()) {
            byte[] buf = new byte[18];
            RANDOM.nextBytes(buf);
            token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
            site = siteRepository.setVerifyToken(companyId, token);
        }
        return site;
    }

    /** Consulta o TXT do domínio e marca verified se encontrar {@code _meada-verify=<token>}. */
    @Transactional
    public CmsSite verifyDomain(UUID companyId) {
        CmsSite site = getOrCreateSite(companyId);
        if (site.domain() == null) {
            throw new NoDomainException();
        }
        if (site.verifyToken() == null || site.verifyToken().isBlank()) {
            site = startDomainVerification(companyId);
        }
        String expected = VERIFY_PREFIX + site.verifyToken();
        List<String> txt = dnsResolver.txtRecords(site.domain());
        boolean ok = txt.stream().map(String::trim).anyMatch(expected::equals);
        return siteRepository.setVerified(companyId, ok);
    }

    // ---- PÁGINAS -------------------------------------------------------------

    public List<CmsPage> listPages(UUID companyId) {
        return pageRepository.listByCompany(companyId);
    }

    public Optional<CmsPage> getPage(UUID companyId, UUID id) {
        return pageRepository.findById(companyId, id);
    }

    /**
     * Cria uma página. A 1ª página do company vira home automaticamente. pageSlug normalizado e único.
     */
    @Transactional
    public CmsPage createPage(UUID companyId, String rawSlug, String title) {
        if (pageRepository.countByCompany(companyId) >= MAX_PAGES) {
            throw new TooManyPagesException();
        }
        String slug = normalizeSlug(rawSlug);
        if (pageRepository.slugExists(companyId, slug)) {
            throw new PageSlugTakenException();
        }
        boolean firstPage = pageRepository.countByCompany(companyId) == 0;
        try {
            return pageRepository.insert(companyId, slug, title == null ? "" : title, firstPage);
        } catch (DataIntegrityViolationException e) {
            throw new PageSlugTakenException();
        }
    }

    /** Salva conteúdo (title/blocks/published) de uma página. Valida/normaliza os blocks. */
    @Transactional
    public CmsPage savePageContent(UUID companyId, UUID id, String title, JsonNode blocks, Boolean published) {
        String blocksJson = null;
        if (blocks != null) {
            try {
                blocksJson = objectMapper.writeValueAsString(normalizeBlocks(blocks));
            } catch (Exception e) {
                throw new InvalidBlocksException();
            }
        }
        return pageRepository.updateContent(companyId, id, title, blocksJson, published)
            .orElseThrow(PageNotFoundException::new);
    }

    @Transactional
    public CmsPage setHome(UUID companyId, UUID id) {
        return pageRepository.setHome(companyId, id).orElseThrow(PageNotFoundException::new);
    }

    /** Exclui uma página. NÃO deixa excluir a única home se houver outras páginas (evita site órfão). */
    @Transactional
    public void deletePage(UUID companyId, UUID id) {
        CmsPage page = pageRepository.findById(companyId, id).orElseThrow(PageNotFoundException::new);
        if (!pageRepository.delete(companyId, id)) {
            throw new PageNotFoundException();
        }
        // se excluiu a home e ainda há páginas, promove a primeira (por position) a home.
        if (page.isHome()) {
            pageRepository.listByCompany(companyId).stream().findFirst()
                .ifPresent(p -> pageRepository.setHome(companyId, p.id()));
        }
    }

    // ---- PÚBLICO -------------------------------------------------------------

    /** Home publicada por slug da empresa (site publicado + página home publicada). */
    public Optional<CmsPage> publishedHomeBySlug(String companySlug) {
        UUID companyId = siteRepository.companyIdBySlug(companySlug);
        if (companyId == null) {
            return Optional.empty();
        }
        return siteRepository.findByCompany(companyId).filter(CmsSite::published)
            .flatMap(s -> pageRepository.findHome(companyId))
            .filter(CmsPage::published);
    }

    /** Página publicada por (slug da empresa, pageSlug). */
    public Optional<CmsPage> publishedPageBySlug(String companySlug, String pageSlug) {
        UUID companyId = siteRepository.companyIdBySlug(companySlug);
        if (companyId == null) {
            return Optional.empty();
        }
        return siteRepository.findByCompany(companyId).filter(CmsSite::published)
            .flatMap(s -> pageRepository.findBySlug(companyId, pageSlug))
            .filter(CmsPage::published);
    }

    /** Home publicada por domínio VERIFICADO (host custom). */
    public Optional<CmsPage> publishedHomeByDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return siteRepository.findByVerifiedDomain(domain.trim().toLowerCase())
            .flatMap(s -> pageRepository.findHome(s.companyId()))
            .filter(CmsPage::published);
    }

    /** Página interna publicada por domínio VERIFICADO + pageSlug. */
    public Optional<CmsPage> publishedPageByDomain(String domain, String pageSlug) {
        if (domain == null || domain.isBlank()) {
            return Optional.empty();
        }
        return siteRepository.findByVerifiedDomain(domain.trim().toLowerCase())
            .flatMap(s -> pageRepository.findBySlug(s.companyId(), pageSlug))
            .filter(CmsPage::published);
    }

    /** Lista de páginas publicadas (pro menu de navegação público). */
    public List<CmsPage> publishedNav(UUID companyId) {
        return pageRepository.listByCompany(companyId).stream().filter(CmsPage::published).toList();
    }

    public Optional<CmsSite> siteByCompany(UUID companyId) {
        return siteRepository.findByCompany(companyId);
    }

    /**
     * O domínio pode receber cert HTTPS on-demand (Caddy)? Só se está VERIFICADO e o site PUBLICADO
     * — impede emissão de cert pra domínios não comprovados/apontados por terceiros.
     */
    public boolean domainAllowedForTls(String domain) {
        if (domain == null || domain.isBlank()) {
            return false;
        }
        return siteRepository.findByVerifiedDomain(domain.trim().toLowerCase()).isPresent();
    }

    // ---- resolução pública por slug (roteamento de domínios) -----------------

    /** Resultado da resolução de {empresa}.dominio: existe? perfil? subdomínio do perfil? tem CMS? */
    public record CompanyResolve(boolean exists, String profileId,
                                 String profileSubdomain, boolean hasCms) {}

    /**
     * Resolve um slug de subdomínio para uma empresa (roteamento {empresa}.meadadigital.com).
     * Empresa suspensa é tratada como inexistente (não serve site nem login). Mapeia o
     * profile_id para o subdomínio do nicho (destino do redirect "login do nicho" quando sem CMS).
     * Optional vazio = não há empresa ativa com esse slug → o middleware manda pra raiz.
     */
    public Optional<CompanyResolve> resolvePublicCompany(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return siteRepository.resolveBySlug(slug.trim().toLowerCase())
            .filter(r -> !"suspended".equals(r.status()))
            .map(r -> {
                String profSub = com.meada.profiles.ProfileType.fromId(r.profileId())
                    .map(com.meada.profiles.ProfileType::subdomain)
                    .orElse("meada");
                return new CompanyResolve(true, r.profileId(), profSub, r.hasCms());
            });
    }

    // ---- helpers -------------------------------------------------------------

    private String normalizeSlug(String raw) {
        if (raw == null) {
            throw new InvalidPageSlugException();
        }
        String slug = raw.trim().toLowerCase().replaceAll("\\s+", "-");
        if (!PAGE_SLUG.matcher(slug).matches() || slug.length() > 80) {
            throw new InvalidPageSlugException();
        }
        return slug;
    }

    /**
     * Normaliza os blocks da página. O page builder evoluiu de uma lista FLAT de blocos para uma
     * ÁRVORE (linhas → colunas → blocos). Aceita AMBOS os formatos (retrocompat): detecta o flat
     * legado por shape e o converte em árvore; valida a árvore recursivamente. O campo persistido
     * continua se chamando {@code blocks}, agora contendo {@code CmsRow[]}.
     */
    private ArrayNode normalizeBlocks(JsonNode blocks) {
        if (blocks == null || !blocks.isArray()) {
            throw new InvalidBlocksException();
        }
        if (isFlatLegacy(blocks)) {
            return normalizeTree(flatToTree(blocks));
        }
        return normalizeTree(blocks);
    }

    /** Flat legado = array não-vazio cujo 1º item tem {@code type} e NÃO tem {@code columns}. */
    private boolean isFlatLegacy(JsonNode arr) {
        if (arr.size() == 0) {
            return false;
        }
        JsonNode first = arr.get(0);
        return first.isObject() && first.has("type") && !first.has("columns");
    }

    /** flat → árvore: cada bloco vira 1 linha de 1 coluna span-12 (passthrough). Espelha o TS. */
    private ArrayNode flatToTree(JsonNode flat) {
        ArrayNode rows = objectMapper.createArrayNode();
        for (JsonNode b : flat) {
            String bid = b.path("id").asText(null);
            String suffix = (bid == null || bid.isBlank()) ? UUID.randomUUID().toString() : bid;
            ObjectNode col = objectMapper.createObjectNode();
            col.put("id", "c-" + suffix);
            col.put("width", 12);
            ArrayNode colBlocks = objectMapper.createArrayNode();
            colBlocks.add(b);
            col.set("blocks", colBlocks);

            ObjectNode row = objectMapper.createObjectNode();
            row.put("id", "r-" + suffix);
            row.set("props", objectMapper.createObjectNode());
            ArrayNode cols = objectMapper.createArrayNode();
            cols.add(col);
            row.set("columns", cols);
            rows.add(row);
        }
        return rows;
    }

    /** Valida e normaliza a árvore: rows[] → columns[] → blocks[] (folha com type ∈ enum). */
    private ArrayNode normalizeTree(JsonNode rows) {
        if (!rows.isArray() || rows.size() > MAX_ROWS) {
            throw new InvalidBlocksException();
        }
        int[] leafCount = {0};
        ArrayNode outRows = objectMapper.createArrayNode();
        for (JsonNode row : rows) {
            if (!row.isObject()) {
                throw new InvalidBlocksException();
            }
            JsonNode cols = row.get("columns");
            if (cols == null || !cols.isArray() || cols.size() > MAX_COLS_ROW) {
                throw new InvalidBlocksException();
            }
            ObjectNode outRow = objectMapper.createObjectNode();
            outRow.put("id", nonBlankId(row, "r-"));
            outRow.set("props", row.has("props") && row.get("props").isObject()
                ? row.get("props") : objectMapper.createObjectNode());

            ArrayNode outCols = objectMapper.createArrayNode();
            for (JsonNode col : cols) {
                if (!col.isObject()) {
                    throw new InvalidBlocksException();
                }
                JsonNode colBlocks = col.get("blocks");
                if (colBlocks == null || !colBlocks.isArray()) {
                    throw new InvalidBlocksException();
                }
                ArrayNode outBlocks = objectMapper.createArrayNode();
                for (JsonNode b : colBlocks) {
                    outBlocks.add(normalizeLeaf(b, leafCount));
                }
                ObjectNode outCol = objectMapper.createObjectNode();
                outCol.put("id", nonBlankId(col, "c-"));
                outCol.set("width", normalizeWidth(col.get("width")));
                outCol.set("blocks", outBlocks);
                outCols.add(outCol);
            }
            outRow.set("columns", outCols);
            outRows.add(outRow);
        }
        return outRows;
    }

    /** Valida um bloco-folha {@code {id,type,props}} (type ∈ enum) e conta o total de folhas. */
    private ObjectNode normalizeLeaf(JsonNode b, int[] leafCount) {
        if (!b.isObject()) {
            throw new InvalidBlocksException();
        }
        String type = b.path("type").asText(null);
        if (CmsBlockType.fromId(type).isEmpty()) {
            throw new InvalidBlocksException();
        }
        leafCount[0]++;
        if (leafCount[0] > MAX_BLOCKS) {
            throw new InvalidBlocksException();
        }
        JsonNode props = b.get("props");
        if (props == null || !props.isObject()) {
            props = objectMapper.createObjectNode();
        }
        ObjectNode norm = objectMapper.createObjectNode();
        norm.put("id", nonBlankId(b, "b-"));
        norm.put("type", type);
        norm.set("props", props);
        return norm;
    }

    /** width: int 1..12 (clamp) ou "auto" (default tolerante p/ dados ruins). */
    private JsonNode normalizeWidth(JsonNode w) {
        if (w != null && w.isInt()) {
            return IntNode.valueOf(Math.max(1, Math.min(12, w.asInt())));
        }
        return TextNode.valueOf("auto");
    }

    private String nonBlankId(JsonNode n, String prefix) {
        String id = n.path("id").asText(null);
        return (id == null || id.isBlank()) ? prefix + UUID.randomUUID() : id;
    }
}
