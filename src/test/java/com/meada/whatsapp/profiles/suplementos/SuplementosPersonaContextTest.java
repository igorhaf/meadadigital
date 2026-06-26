package com.meada.whatsapp.profiles.suplementos;

import com.meada.whatsapp.AbstractIntegrationTest;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProduct;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o {@link SuplementosMenuCache} (camada 8.24): (1) o bloco de catálogo lista os produtos por
 * categoria com a marca e as VARIANTES (variant_id + sabor/peso + preço + estoque); (2) 🔒 o coração
 * desta SM — o bloco de instruções CARREGA A TRAVA DE SAÚDE / NÃO-PRESCRIÇÃO (espelho leve do nutri):
 * a IA não prescreve dosagem/uso e encaminha a um profissional, e o aviso "não substitui orientação".
 * São tokens estáveis assertados — se alguém remover a trava do prompt, o teste falha.
 */
class SuplementosPersonaContextTest extends AbstractIntegrationTest {

    @Autowired
    private SuplementosMenuCache menuCache;
    @Autowired
    private SupProductService productService;

    private static final UUID COMPANY = UUID.fromString("c8240000-0000-0000-0000-000000000094");
    private static final UUID USER = UUID.fromString("d8240000-0000-0000-0000-000000000094");

    @BeforeEach
    void seed() {
        jdbcTemplate.update("insert into companies (id, name, slug, profile_id) values (?, ?, ?, 'suplementos')",
            COMPANY, "Suplementos P", "suplementos-p");
        jdbcTemplate.update("insert into auth.users (id) values (?) on conflict (id) do nothing", USER);
        jdbcTemplate.update("insert into users (id, company_id, email, role) values (?, ?, 'u@sup-p.dev', 'admin')",
            USER, COMPANY);
    }

    @Test
    @DisplayName("🔒 o bloco de contexto contém a TRAVA DE SAÚDE: não prescrever dosagem/uso + encaminhar + não substitui")
    void healthTrava_isPresent() {
        String segment = menuCache.menuSegment(COMPANY);

        // tokens estáveis da trava de não-prescrição (espelho leve do nutri).
        assertThat(segment).contains("dosagem");
        assertThat(segment).contains("nutricionista");
        assertThat(segment).containsIgnoringCase("NÃO SUBSTITUI");
        // a IA não recomenda por objetivo/sintoma e ensina a tag de pedido.
        assertThat(segment).contains("<pedido_suplementos>");
    }

    @Test
    @DisplayName("o bloco de catálogo lista produto (marca) + variante (variant_id, sabor/peso, preço, estoque)")
    void catalog_listsProductsAndVariants() {
        SupProduct p = productService.create(COMPANY, USER, "Whey Protein", "Growth", "proteinas", null);
        var v = productService.addVariant(COMPANY, USER, p.id(), "Chocolate", "900g", null, 14990, 3, null);
        menuCache.invalidate(COMPANY);

        String segment = menuCache.menuSegment(COMPANY);
        assertThat(segment).contains("Whey Protein");
        assertThat(segment).contains("Growth");                 // marca
        assertThat(segment).contains(v.id().toString());        // variant_id exato pra IA emitir a tag
        assertThat(segment).contains("Chocolate 900g");         // label da variante
        assertThat(segment).contains("em estoque");
    }

    @Test
    @DisplayName("variante esgotada aparece marcada 'esgotado' (a IA não deve oferecer)")
    void catalog_marksOutOfStock() {
        SupProduct p = productService.create(COMPANY, USER, "Creatina", null, "aminoacidos", null);
        productService.addVariant(COMPANY, USER, p.id(), null, "300g", null, 9990, 0, null);
        menuCache.invalidate(COMPANY);

        String segment = menuCache.menuSegment(COMPANY);
        assertThat(segment).contains("esgotado");
    }
}
