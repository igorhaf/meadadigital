package com.meada.cms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.AbstractIntegrationTest;
import com.meada.cms.CmsService.DomainTakenException;
import com.meada.cms.CmsService.InvalidBlocksException;
import com.meada.cms.CmsService.InvalidDomainException;
import com.meada.cms.CmsService.InvalidPageSlugException;
import com.meada.cms.CmsService.PageSlugTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o CmsService multi-página (SM-N): site (publish/theme/domain), páginas (CRUD/home/slug),
 * validação de blocks, verificação de domínio (DNS TXT mockado) e resolução pública.
 */
@Import(CmsServiceTest.TestConfig.class)
class CmsServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CmsService service;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private FakeDnsResolver fakeDns;

    private static final UUID CO_A = UUID.fromString("cf000000-0000-0000-0000-000000000001");
    private static final UUID CO_B = UUID.fromString("cf000000-0000-0000-0000-000000000002");

    @BeforeEach
    void seed() {
        fakeDns.reset();
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'nutri')", CO_A, "Empresa A", "empresa-a");
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'oficina')", CO_B, "Empresa B", "empresa-b");
    }

    @Test
    @DisplayName("1ª página criada vira home automaticamente; 2ª não")
    void firstPageIsHome() {
        CmsPage p1 = service.createPage(CO_A, "inicio", "Início");
        assertThat(p1.isHome()).isTrue();
        CmsPage p2 = service.createPage(CO_A, "servicos", "Serviços");
        assertThat(p2.isHome()).isFalse();
        assertThat(service.listPages(CO_A)).hasSize(2);
    }

    @Test
    @DisplayName("page_slug normalizado (espaços→hífen, lower) e único por company")
    void pageSlug_normalizeAndUnique() {
        CmsPage p = service.createPage(CO_A, "  Sobre Nos  ", "X");
        assertThat(p.pageSlug()).isEqualTo("sobre-nos"); // trim + lower + espaços→hífen
        // acento/pontuação não passam no formato de slug.
        assertThatThrownBy(() -> service.createPage(CO_A, "INVALIDO!!", "X")).isInstanceOf(InvalidPageSlugException.class);
        service.createPage(CO_A, "servicos", "S");
        assertThatThrownBy(() -> service.createPage(CO_A, "servicos", "S2")).isInstanceOf(PageSlugTakenException.class);
    }

    @Test
    @DisplayName("savePageContent normaliza blocks (8 tipos válidos); type inválido → InvalidBlocks")
    void savePage_blocks() throws Exception {
        CmsPage p = service.createPage(CO_A, "home", "Home");
        var blocks = objectMapper.readTree("""
            [ {"type":"hero","props":{"title":"Oi"}}, {"type":"faq","props":{"items":[]}},
              {"type":"gallery","props":{}}, {"type":"map","props":{}} ]
            """);
        CmsPage saved = service.savePageContent(CO_A, p.id(), "Home", blocks, true);
        assertThat(saved.blocks()).hasSize(4);
        assertThat(saved.published()).isTrue();
        assertThat(saved.blocks().get(0).get("id").asText()).isNotBlank();

        var bad = objectMapper.readTree("[ {\"type\":\"naoexiste\",\"props\":{}} ]");
        assertThatThrownBy(() -> service.savePageContent(CO_A, p.id(), "X", bad, null)).isInstanceOf(InvalidBlocksException.class);
    }

    @Test
    @DisplayName("setHome troca a home (1 home por company)")
    void setHome_switches() {
        CmsPage a = service.createPage(CO_A, "a", "A"); // home
        CmsPage b = service.createPage(CO_A, "b", "B");
        service.setHome(CO_A, b.id());
        List<CmsPage> pages = service.listPages(CO_A);
        assertThat(pages.stream().filter(CmsPage::isHome)).hasSize(1);
        assertThat(pages.stream().filter(CmsPage::isHome).findFirst().orElseThrow().id()).isEqualTo(b.id());
        assertThat(service.getPage(CO_A, a.id()).orElseThrow().isHome()).isFalse();
    }

    @Test
    @DisplayName("excluir a home promove outra página a home")
    void deleteHome_promotes() {
        CmsPage a = service.createPage(CO_A, "a", "A"); // home
        CmsPage b = service.createPage(CO_A, "b", "B");
        service.deletePage(CO_A, a.id());
        assertThat(service.getPage(CO_A, b.id()).orElseThrow().isHome()).isTrue();
    }

    @Test
    @DisplayName("setDomain válido/limpa; inválido e duplicado falham")
    void domain() {
        assertThat(service.setDomain(CO_A, "Loja.com.BR").domain()).isEqualTo("loja.com.br");
        assertThatThrownBy(() -> service.setDomain(CO_A, "x.meadadigital.com")).isInstanceOf(InvalidDomainException.class);
        assertThatThrownBy(() -> service.setDomain(CO_B, "loja.com.br")).isInstanceOf(DomainTakenException.class);
        assertThat(service.setDomain(CO_A, "  ").domain()).isNull();
    }

    @Test
    @DisplayName("bloqueio do domínio Meada é por label: 'minhameadadigital.com' passa; raiz e subdomínio não")
    void domain_meadaSuffixByLabel() {
        // Regressão: endsWith("meadadigital.com") SEM ponto rejeitava domínios legítimos de
        // terceiros que apenas terminam com a string (sem separador de label).
        assertThat(service.setDomain(CO_A, "minhameadadigital.com").domain())
            .isEqualTo("minhameadadigital.com");
        assertThatThrownBy(() -> service.setDomain(CO_A, "meadadigital.com"))
            .isInstanceOf(InvalidDomainException.class);
        assertThatThrownBy(() -> service.setDomain(CO_A, "app.meadadigital.local"))
            .isInstanceOf(InvalidDomainException.class);
    }

    @Test
    @DisplayName("verifyDomain: TXT com o token → verified=true; sem → false")
    void verify() {
        service.setDomain(CO_A, "loja.com.br");
        CmsSite started = service.startDomainVerification(CO_A);
        assertThat(started.verifyToken()).isNotBlank();
        // sem TXT → não verifica.
        assertThat(service.verifyDomain(CO_A).domainVerified()).isFalse();
        // com o TXT certo → verifica.
        fakeDns.set("loja.com.br", List.of("_meada-verify=" + started.verifyToken()));
        assertThat(service.verifyDomain(CO_A).domainVerified()).isTrue();
    }

    @Test
    @DisplayName("trocar o domínio zera a verificação")
    void changingDomainResetsVerification() {
        service.setDomain(CO_A, "loja.com.br");
        CmsSite s = service.startDomainVerification(CO_A);
        fakeDns.set("loja.com.br", List.of("_meada-verify=" + s.verifyToken()));
        assertThat(service.verifyDomain(CO_A).domainVerified()).isTrue();
        // troca o domínio → verified volta a false.
        assertThat(service.setDomain(CO_A, "outraloja.com.br").domainVerified()).isFalse();
    }

    @Test
    @DisplayName("resolução pública: home/página só quando site E página publicados; por domínio só verificado")
    void publicResolution() {
        CmsPage home = service.createPage(CO_A, "home", "Home");
        CmsPage svc = service.createPage(CO_A, "servicos", "Serviços");
        // nada publicado ainda.
        assertThat(service.publishedHomeBySlug("empresa-a")).isEmpty();
        service.savePageContent(CO_A, home.id(), "Home", null, true);
        service.savePageContent(CO_A, svc.id(), "Serviços", null, true);
        assertThat(service.publishedHomeBySlug("empresa-a")).isEmpty(); // site ainda rascunho
        service.setPublished(CO_A, true);
        assertThat(service.publishedHomeBySlug("empresa-a")).isPresent();
        assertThat(service.publishedPageBySlug("empresa-a", "servicos")).isPresent();
        // por domínio: precisa verificado.
        service.setDomain(CO_A, "loja.com.br");
        CmsSite s = service.startDomainVerification(CO_A);
        assertThat(service.publishedHomeByDomain("loja.com.br")).isEmpty(); // não verificado
        fakeDns.set("loja.com.br", List.of("_meada-verify=" + s.verifyToken()));
        service.verifyDomain(CO_A);
        assertThat(service.publishedHomeByDomain("loja.com.br")).isPresent();
        assertThat(service.domainAllowedForTls("loja.com.br")).isTrue();
        assertThat(service.domainAllowedForTls("naoexiste.com")).isFalse();
    }

    // ---- migração / árvore de blocos (page builder estrutural) ---------------

    @Test
    @DisplayName("migração: blocks flat legado → árvore (1 linha/1 coluna span-12 por bloco), type preservado")
    void blocks_flatLegacyMigratesToTree() throws Exception {
        CmsPage p = service.createPage(CO_A, "home", "Home");
        var flat = objectMapper.readTree("""
            [ {"id":"b-old1","type":"hero","props":{"title":"Oi"}},
              {"id":"b-old2","type":"text","props":{"body":"x"}} ]
            """);
        CmsPage saved = service.savePageContent(CO_A, p.id(), "Home", flat, true);
        // agora é árvore: 2 linhas (1 por bloco legado).
        assertThat(saved.blocks()).hasSize(2);
        var row0 = saved.blocks().get(0);
        assertThat(row0.has("columns")).isTrue();
        assertThat(row0.get("columns")).hasSize(1);
        var col0 = row0.get("columns").get(0);
        assertThat(col0.get("width").asInt()).isEqualTo(12);
        assertThat(col0.get("blocks")).hasSize(1);
        assertThat(col0.get("blocks").get(0).get("type").asText()).isEqualTo("hero");
        assertThat(col0.get("blocks").get(0).get("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("árvore com 2 colunas passa intacta; widths preservados; re-salvar é idempotente")
    void blocks_treePassesThroughAndIdempotent() throws Exception {
        CmsPage p = service.createPage(CO_A, "home", "Home");
        var tree = objectMapper.readTree("""
            [ { "id":"r1", "props":{"bg":"muted"}, "columns":[
                  {"id":"c1","width":6,"blocks":[{"id":"b1","type":"hero","props":{}}]},
                  {"id":"c2","width":6,"blocks":[{"id":"b2","type":"text","props":{"body":"y"}}]} ] } ]
            """);
        CmsPage saved = service.savePageContent(CO_A, p.id(), "Home", tree, true);
        assertThat(saved.blocks()).hasSize(1);
        var cols = saved.blocks().get(0).get("columns");
        assertThat(cols).hasSize(2);
        assertThat(cols.get(0).get("width").asInt()).isEqualTo(6);
        assertThat(cols.get(1).get("width").asInt()).isEqualTo(6);
        // idempotência: re-salvar a saída normalizada dá o mesmo formato.
        CmsPage again = service.savePageContent(CO_A, p.id(), "Home", objectMapper.valueToTree(saved.blocks()), true);
        assertThat(again.blocks().get(0).get("columns")).hasSize(2);
        assertThat(again.blocks().get(0).get("columns").get(0).get("width").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("árvore com type de bloco inválido numa coluna → InvalidBlocks")
    void blocks_treeInvalidLeafType() throws Exception {
        CmsPage p = service.createPage(CO_A, "home", "Home");
        var bad = objectMapper.readTree("""
            [ { "id":"r1", "props":{}, "columns":[
                  {"id":"c1","width":12,"blocks":[{"id":"b1","type":"naoexiste","props":{}}]} ] } ]
            """);
        assertThatThrownBy(() -> service.savePageContent(CO_A, p.id(), "X", bad, null))
            .isInstanceOf(InvalidBlocksException.class);
    }

    @Test
    @DisplayName("width fora de 1..12 sofre clamp; ausente/inválido vira \"auto\"")
    void blocks_widthNormalization() throws Exception {
        CmsPage p = service.createPage(CO_A, "home", "Home");
        var tree = objectMapper.readTree("""
            [ { "id":"r1", "props":{}, "columns":[
                  {"id":"c1","width":99,"blocks":[{"id":"b1","type":"hero","props":{}}]},
                  {"id":"c2","blocks":[{"id":"b2","type":"text","props":{}}]} ] } ]
            """);
        CmsPage saved = service.savePageContent(CO_A, p.id(), "Home", tree, true);
        var cols = saved.blocks().get(0).get("columns");
        assertThat(cols.get(0).get("width").asInt()).isEqualTo(12);      // 99 → clamp 12
        assertThat(cols.get(1).get("width").asText()).isEqualTo("auto"); // ausente → "auto"
    }

    // ---- fake DNS ------------------------------------------------------------

    static class FakeDnsResolver implements DnsTxtResolver {
        private final List<String[]> entries = new CopyOnWriteArrayList<>(); // [host, value]
        void reset() { entries.clear(); }
        void set(String host, List<String> values) {
            for (String v : values) entries.add(new String[] {host, v});
        }
        @Override
        public List<String> txtRecords(String host) {
            List<String> out = new ArrayList<>();
            for (String[] e : entries) if (e[0].equals(host)) out.add(e[1]);
            return out;
        }
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        FakeDnsResolver fakeDnsResolver() {
            return new FakeDnsResolver();
        }
    }
}
