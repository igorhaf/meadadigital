package com.meada.whatsapp.profiles.suplementos;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProduct;
import com.meada.whatsapp.profiles.suplementos.catalog.SupProductRepository;
import com.meada.whatsapp.profiles.suplementos.catalog.SupVariant;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de catálogo+config injetado no prompt do SuplementosBot (camada 8.24). Clone do
 * {@link com.meada.whatsapp.profiles.lingerie.LingerieMenuCache} (Caffeine TTL 60s, chassi de varejo)
 * — o {@link com.meada.whatsapp.profiles.suplementos.catalog.SupProductService} chama
 * {@link #invalidate} ao mutar produto/variante/config, então a IA vê a mudança na hora. IGNORA o
 * conversationId (o contexto é o catálogo).
 *
 * <p>DUAS coisas distinguem este cache dos demais perfis de varejo:
 * <ul>
 *   <li>⭐ ESCAPADA 1: sob cada PRODUTO (com marca), lista as VARIANTES (sabor×peso) com o variant_id
 *       EXATO + preço + ESTOQUE — a IA precisa do variant_id pra emitir a tag
 *       {@code <pedido_suplementos>} e do estoque pra NÃO oferecer variante esgotada.</li>
 *   <li>🔒 ESCAPADA 2 (o coração): o bloco de instruções carrega a TRAVA DE SAÚDE / NÃO-PRESCRIÇÃO
 *       (espelho leve da trava clínica do {@code ProfilePromptContext.NUTRI} +
 *       {@code NutriContextCache}, adaptada a varejo): a IA NUNCA prescreve dosagem/uso, NUNCA
 *       recomenda por objetivo/sintoma, encaminha a nutricionista/médico/educador físico, e inclui o
 *       aviso "não substitui orientação de um profissional de saúde". A trava vive AQUI (contexto) E na
 *       persona (ProfilePromptContext) — 2 lugares, igual nutri.</li>
 * </ul>
 */
@Component
public class SuplementosMenuCache {

    private final SupProductRepository productRepository;
    private final SuplementosConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public SuplementosMenuCache(SupProductRepository productRepository,
                                SuplementosConfigRepository configRepository) {
        this.productRepository = productRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de catálogo+config+instruções (com a trava de saúde) para o prompt, cacheado por company (TTL 60s). */
    public String menuSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo SupProductService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<SupProduct> products = productRepository.listByCompany(companyId, null, true);
        SuplementosConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (products.isEmpty()) {
            sb.append("CATÁLOGO DISPONÍVEL HOJE: (nenhum produto disponível no momento — informe o "
                + "cliente que o catálogo está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CATÁLOGO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (SupProduct p : products) {
                if (!p.category().equals(currentCategory)) {
                    currentCategory = p.category();
                    sb.append("[").append(SuplementosCategory.fromId(currentCategory)
                        .map(SuplementosCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(p.id()).append(" · ").append(p.name());
                if (p.brand() != null && !p.brand().isBlank()) {
                    sb.append(" · ").append(p.brand().strip());
                }
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append(" · ").append(p.description().strip());
                }
                sb.append("\n");
                appendVariants(sb, p);
            }
            sb.append("\n");
        }

        // 🔒 TRAVA DE SAÚDE / NÃO-PRESCRIÇÃO (espelho leve do nutri, adaptada a varejo).
        sb.append("LIMITES DE SAÚDE (OBRIGATÓRIOS — LEIA COM ATENÇÃO):\n")
            .append("Você é um atendente de loja: mostra o catálogo, tira dúvida de PRODUTO (sabor, peso, preço, "
                + "disponibilidade, estoque) e monta o pedido — NADA além disso. NUNCA prescreva dosagem, posologia, "
                + "uso ou horário de tomar. NUNCA recomende um suplemento como tratamento ou conduta por objetivo "
                + "(emagrecer, ganhar massa, curar) ou por sintoma. NUNCA responda 'isso serve pra [objetivo]?', "
                + "'quanto tomar?', 'posso tomar com [remédio]?' ou 'qual o melhor pra mim?'. NUNCA opine sobre saúde, "
                + "patologia, interação medicamentosa ou contraindicação. Para QUALQUER dúvida de uso, dosagem ou "
                + "objetivo de saúde, acolha e ORIENTE consultar um nutricionista, médico ou educador físico ('Para "
                + "isso, o ideal é conversar com um nutricionista ou médico — eu não posso orientar sobre uso ou "
                + "dosagem.'). Quando fizer sentido, lembre que este produto NÃO SUBSTITUI a orientação de um "
                + "profissional de saúde. NUNCA invente produto/sabor/peso/preço fora do catálogo, e NUNCA prometa "
                + "validade (a data de validade é administrativa interna).\n");

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Quando o cliente CONFIRMAR o pedido (frases como \"pode mandar\", \"confirma\", \"fechou\") e "
                + "tiver informado o endereço de ENTREGA, sua ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha "
                + "própria, sem markdown):\n")
            .append("<pedido_suplementos>{\"delivery_address\":\"...\",\"items\":[{\"variant_id\":\"UUID_EXATO_DA_"
                + "VARIANTE\",\"qtd\":N}],\"notes\":\"...|null\"}</pedido_suplementos>\n")
            .append("Cada item referencia o variant_id EXATO de uma VARIANTE (combinação sabor×peso) do catálogo "
                + "acima — NUNCA o product_id. Só ofereça variantes COM estoque (as marcadas \"esgotado\" NÃO podem "
                + "ser pedidas). SÓ ENTREGA: o \"delivery_address\" é OBRIGATÓRIO. ANTES da tag, escreva a confirmação "
                + "humana normal (\"Confirmado: 1 Whey Chocolate 900g, total R$ X, entrega na Rua Y 💪\"). NÃO emita a "
                + "tag enquanto o cliente ainda monta o pedido — só na confirmação final COM endereço.\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total).\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise o cliente se o pedido ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents()).append("\n");
        sb.append("Avise o cliente que o pedido ficará AGUARDANDO confirmação da loja.\n\n");

        return sb.toString();
    }

    /**
     * Lista as variantes active=true do produto, uma linha com o variant_id + [sabor/peso] + preço +
     * estoque (N em estoque / esgotado): {@code variant_id [flavor sizeLabel] R$ p (N em estoque) | ...}.
     */
    private void appendVariants(StringBuilder sb, SupProduct p) {
        List<SupVariant> variants = p.variants();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (SupVariant v : variants) {
            if (!v.active()) {
                continue;
            }
            if (line.length() > 0) {
                line.append(" | ");
            }
            String stock = v.stockQuantity() > 0 ? (v.stockQuantity() + " em estoque") : "esgotado";
            line.append(v.id()).append(" [").append(v.label()).append("] ")
                .append("R$ ").append(formatBrl(v.priceCents())).append(" (").append(stock).append(")");
        }
        if (line.length() > 0) {
            sb.append("    ").append(line).append("\n");
        }
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
