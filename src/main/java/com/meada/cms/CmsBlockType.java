package com.meada.cms;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogo HARDCODED de tipos de bloco do CMS (SM-M, page builder). Cada bloco de uma página é
 * {@code {id, type, props}}; {@code type} é um destes. As props variam por tipo (validadas
 * app-level no {@code CmsService}; o JSONB não tem CHECK pra deixar os blocos evoluírem).
 *
 * <p>Espelhado 1:1 por {@code frontend/lib/cms/cms-block-type.ts} ({@code CmsBlockTypeParityTest}
 * garante a paridade). Adicionar um tipo = editar os 2 arquivos + o editor/render do frontend +
 * rodar a paridade. Tipos iniciais:
 * <ul>
 *   <li>{@code hero} — título + subtítulo + botão (label/href) + badge/imagem/2º botão.</li>
 *   <li>{@code text} — conteúdo livre em markdown.</li>
 *   <li>{@code services} — título + lista de itens (name, description, price).</li>
 *   <li>{@code contact} — telefone/WhatsApp, endereço, horário + botão.</li>
 * </ul>
 *
 * <p>Catálogo ampliado (estilo alegria): blocos de conteúdo (stats, feature_grid, image_text_split,
 * steps, columns), de marketing (banner_strip, marquee, quote, cta) e de pacotes (packages). As props
 * de todos são JSONB livre — adicionar campo a um bloco não toca o backend; só adicionar TIPO novo
 * (como estes) exige editar este enum + o mirror TS + o render + rodar a paridade.
 *
 * <p>Onda 1 de blocos genéricos de site (docs/FEATURES_SUGERIDAS_CMS.md): prova social e mídia —
 * reviews_carousel (avaliações estilo Google), video (YouTube/Vimeo), rating_badge (selo de nota
 * agregada) e logo_strip (faixa de logos/selos).
 */
public enum CmsBlockType {
    HERO("hero"),
    TEXT("text"),
    SERVICES("services"),
    CONTACT("contact"),
    GALLERY("gallery"),
    FAQ("faq"),
    TESTIMONIALS("testimonials"),
    MAP("map"),
    BANNER_STRIP("banner_strip"),
    STATS("stats"),
    FEATURE_GRID("feature_grid"),
    IMAGE_TEXT_SPLIT("image_text_split"),
    STEPS("steps"),
    COLUMNS("columns"),
    PACKAGES("packages"),
    MARQUEE("marquee"),
    QUOTE("quote"),
    CTA("cta"),
    // Onda 1 de blocos genéricos de site (prova social e mídia):
    REVIEWS_CAROUSEL("reviews_carousel"),
    VIDEO("video"),
    RATING_BADGE("rating_badge"),
    LOGO_STRIP("logo_strip"),
    // Blocos da marca Meada (preset meada-dark) — identidade visual própria (não tematizáveis):
    MEADA_HERO("meada_hero"),
    MEADA_SERVICES("meada_services"),
    MEADA_PORTFOLIO("meada_portfolio"),
    MEADA_CTA("meada_cta"),
    MEADA_NAVBAR("meada_navbar"),
    MEADA_FOOTER("meada_footer"),
    NICHES_GRID("niches_grid");

    private final String id;

    CmsBlockType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<CmsBlockType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(t -> t.id.equals(id)).findFirst();
    }

    public static List<CmsBlockType> allActive() {
        return List.of(values());
    }
}
