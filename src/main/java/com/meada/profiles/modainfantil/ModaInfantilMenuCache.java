package com.meada.profiles.modainfantil;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.modainfantil.catalog.ModaInfantilProduct;
import com.meada.profiles.modainfantil.catalog.ModaInfantilProductRepository;
import com.meada.profiles.modainfantil.catalog.ModaInfantilVariant;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de catálogo+config injetado no prompt do ModaInfantilBot (camada 8.22). Clone do
 * {@link com.meada.profiles.lingerie.LingerieMenuCache} (Caffeine TTL 60s) — o
 * {@link com.meada.profiles.modainfantil.catalog.ModaInfantilProductService} chama
 * {@link #invalidate} ao mutar produto/variante/config, então a IA vê a mudança na hora. Ignora o
 * conversationId (o contexto é só o catálogo).
 *
 * <p>Sob cada PRODUTO, lista as VARIANTES com o variant_id EXATO + faixa-etária/cor/preço + ESTOQUE —
 * a IA precisa do variant_id pra emitir a tag {@code <pedido_moda_infantil>} e do estoque pra NÃO
 * oferecer variante esgotada. ⭐ A faixa etária (size) é uma FAIXA DE IDADE (RN, 0-3m, 1a, ...) e a IA é
 * instruída a SUGERIR o tamanho a partir da idade da criança. Formato por produto:
 * <pre>
 * - &lt;product_id&gt; · &lt;name&gt; · &lt;category&gt;
 *     &lt;variant_id&gt; [1a/Azul] R$ 59,90 (3 em estoque) | &lt;variant_id&gt; [2a/Azul] R$ 59,90 (esgotado)
 * </pre>
 */
@Component
public class ModaInfantilMenuCache {

    private final ModaInfantilProductRepository productRepository;
    private final ModaInfantilConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public ModaInfantilMenuCache(ModaInfantilProductRepository productRepository,
                                 ModaInfantilConfigRepository configRepository) {
        this.productRepository = productRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de catálogo+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String menuSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo ModaInfantilProductService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<ModaInfantilProduct> products = productRepository.listByCompany(companyId, null, true);
        ModaInfantilConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (products.isEmpty()) {
            sb.append("CATÁLOGO DISPONÍVEL HOJE: (nenhum produto disponível no momento — informe a "
                + "cliente que o catálogo está indisponível e ofereça avisá-la quando voltar.)\n\n");
        } else {
            sb.append("CATÁLOGO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (ModaInfantilProduct p : products) {
                if (!p.category().equals(currentCategory)) {
                    currentCategory = p.category();
                    sb.append("[").append(ModaInfantilCategory.fromId(currentCategory)
                        .map(ModaInfantilCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(p.id()).append(" · ").append(p.name())
                    .append(" · ").append(ModaInfantilCategory.fromId(p.category())
                        .map(ModaInfantilCategory::label).orElse(p.category()));
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append(" · ").append(p.description().strip());
                }
                sb.append("\n");
                appendVariants(sb, p);
            }
            sb.append("\n");
        }

        sb.append("SOBRE OS TAMANHOS (faixa etária):\n")
            .append("Os tamanhos das peças são por FAIXA ETÁRIA: RN (recém-nascido), 0-3m, 3-6m, 6-9m, "
                + "9-12m, e depois por ano (1a, 2a, 3a, 4a, 6a, 8a, 10a, 12a). Se a cliente informar a "
                + "IDADE da criança, SUGIRA a faixa etária correspondente (ex.: 18 meses → 1a; 2 anos → "
                + "2a; recém-nascido → RN). Sempre confirme com a cliente antes de fechar — a sugestão é "
                + "uma orientação, não uma garantia de caimento.\n\n");

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Quando a cliente CONFIRMAR o pedido (frases como \"pode mandar\", \"confirma\", "
                + "\"fechou\"), informar a forma de recebimento (entrega ou retirada) e — se for "
                + "ENTREGA — o endereço, sua ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha "
                + "própria, sem markdown):\n")
            .append("<pedido_moda_infantil>{\"items\":[{\"variant_id\":\"UUID_EXATO_DA_VARIANTE\",\"qtd\":N}],"
                + "\"fulfillment\":\"entrega\",\"endereco\":\"...\","
                + "\"cupom\":\"CODIGO_SE_HOUVER\",\"total_cents\":NNN}</pedido_moda_infantil>\n")
            .append("Se o cliente informar um CUPOM de desconto, registre o código no campo \"cupom\" "
                + "(omita se não houver) — quem VALIDA e calcula é o sistema; NUNCA invente desconto "
                + "(cupom inválido: o pedido sai sem o desconto). Se a variante desejada estiver "
                + "ESGOTADA, ofereça avisar quando voltar; se o cliente aceitar, TERMINE a mensagem "
                + "com a tag (linha própria): "
                + "<aviso_estoque_moda>{\"variant_id\":\"UUID_DA_VARIANTE\"}</aviso_estoque_moda> "
                + "— NUNCA prometa data de reposição.\n")
            .append("Cada item referencia o variant_id EXATO de uma VARIANTE (combinação faixa-etária×cor) "
                + "do catálogo acima — NUNCA o product_id. Só ofereça variantes COM estoque (as "
                + "marcadas \"esgotado\" NÃO podem ser pedidas). \"fulfillment\" é \"entrega\" (exige "
                + "\"endereco\") ou \"retirada\" (sem endereço). NUNCA invente produto/tamanho/cor/"
                + "preço fora do catálogo. ANTES da tag, escreva a confirmação humana normal "
                + "(\"Confirmado: 1 Body Manga Longa (1a/Azul), total R$ X, entrega na Rua Y 🧸\"). NÃO "
                + "emita a tag enquanto a cliente ainda monta o pedido — só na confirmação final.\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total apenas em ENTREGA, não em retirada).\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise a cliente se o pedido ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents()).append("\n");
        sb.append("Avise a cliente que o pedido ficará AGUARDANDO confirmação da loja.\n\n");

        return sb.toString();
    }

    /**
     * Lista as variantes available=true do produto, uma linha com o variant_id + [faixa-etária/cor] +
     * preço (variante ou base) + estoque (N em estoque / esgotado): {@code variant_id [size/color]
     * R$ p (N em estoque) | ...}.
     */
    private void appendVariants(StringBuilder sb, ModaInfantilProduct p) {
        List<ModaInfantilVariant> variants = p.variants();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (ModaInfantilVariant v : variants) {
            if (!v.available()) {
                continue;
            }
            if (line.length() > 0) {
                line.append(" | ");
            }
            int price = v.priceCents() != null ? v.priceCents() : p.basePriceCents();
            String stock = v.stockQty() > 0 ? (v.stockQty() + " em estoque") : "esgotado";
            line.append(v.id()).append(" [").append(v.size()).append("/").append(v.color()).append("] ")
                .append("R$ ").append(formatBrl(price)).append(" (").append(stock).append(")");
        }
        if (line.length() > 0) {
            sb.append("    ").append(line).append("\n");
        }
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
