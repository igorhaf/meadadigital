package com.meada.profiles.las;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.las.catalog.LasProduct;
import com.meada.profiles.las.catalog.LasProductRepository;
import com.meada.profiles.las.catalog.LasVariant;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de catálogo+config injetado no prompt do LasBot (camada 8.23). Clone de
 * {@link com.meada.profiles.lingerie.LingerieMenuCache} (Caffeine TTL 60s) — o
 * {@link com.meada.profiles.las.catalog.LasProductService} chama {@link #invalidate} ao mutar
 * produto/variante/config, então a IA vê a mudança na hora.
 *
 * <p>DIFERENÇA do lingerie (⭐ eixo de variante trocado): a variante aqui é COR × DYE_LOT (lote de
 * tingimento) — não tamanho×cor. Sob cada PRODUTO, lista as VARIANTES com o variant_id EXATO +
 * cor/dye_lot/preço + ESTOQUE — a IA precisa do variant_id pra emitir a tag {@code <pedido_las>} e do
 * estoque pra NÃO oferecer variante esgotada. Ensina também a opção {@code same_lot_guaranteed}:
 * novelos do MESMO lote de tingimento têm o MESMO tom; em projetos grandes, garantir o mesmo lote.
 * Formato por produto:
 * <pre>
 * - &lt;product_id&gt; · &lt;name&gt; · &lt;category&gt;
 *     &lt;variant_id&gt; [Azul / L2024-A] R$ 19,90 (8 em estoque) | &lt;variant_id&gt; [Azul / L2024-B] R$ 19,90 (esgotado)
 * </pre>
 */
@Component
public class LasMenuCache {

    private final LasProductRepository productRepository;
    private final LasConfigRepository configRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final Cache<UUID, String> cache;

    public LasMenuCache(LasProductRepository productRepository,
                        LasConfigRepository configRepository,
                        org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.productRepository = productRepository;
        this.configRepository = configRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de catálogo+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String menuSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo LasProductService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<LasProduct> products = productRepository.listByCompany(companyId, null, true);
        LasConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (products.isEmpty()) {
            sb.append("CATÁLOGO DISPONÍVEL HOJE: (nenhum produto disponível no momento — informe a "
                + "cliente que o catálogo está indisponível e ofereça avisá-la quando voltar.)\n\n");
        } else {
            sb.append("CATÁLOGO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (LasProduct p : products) {
                if (!p.category().equals(currentCategory)) {
                    currentCategory = p.category();
                    sb.append("[").append(LasCategory.fromId(currentCategory)
                        .map(LasCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(p.id()).append(" · ").append(p.name())
                    .append(" · ").append(LasCategory.fromId(p.category())
                        .map(LasCategory::label).orElse(p.category()));
                if (p.description() != null && !p.description().isBlank()) {
                    sb.append(" · ").append(p.description().strip());
                }
                sb.append("\n");
                appendVariants(sb, p);
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Quando a cliente CONFIRMAR o pedido (frases como \"pode mandar\", \"confirma\", "
                + "\"fechou\"), informar a forma de recebimento (entrega ou retirada) e — se for "
                + "ENTREGA — o endereço, sua ÚLTIMA mensagem deve TERMINAR com a tag (em uma linha "
                + "própria, sem markdown):\n")
            .append("<pedido_las>{\"items\":[{\"variant_id\":\"UUID_EXATO_DA_VARIANTE\",\"qtd\":N}],"
                + "\"fulfillment\":\"entrega\",\"same_lot_guaranteed\":false,\"endereco\":\"...\","
                + "\"cupom\":\"CODIGO_SE_HOUVER\",\"total_cents\":NNN}</pedido_las>\n")
            .append("Cada item referencia o variant_id EXATO de uma VARIANTE (combinação cor × dye_lot, "
                + "o LOTE DE TINGIMENTO) do catálogo acima — NUNCA o product_id. Só ofereça variantes "
                + "COM estoque (as marcadas \"esgotado\" NÃO podem ser pedidas). \"fulfillment\" é "
                + "\"entrega\" (exige \"endereco\") ou \"retirada\" (sem endereço). "
                + "\"same_lot_guaranteed\": novelos da MESMA cor mas de LOTES (dye_lot) diferentes têm "
                + "variação visível de tom — quem tricota um projeto grande precisa do MESMO lote. "
                + "Quando a cliente exigir o mesmo lote, ponha \"same_lot_guaranteed\":true e use, para "
                + "cada cor, variantes de UM ÚNICO dye_lot (senão o pedido é recusado por lotes "
                + "misturados). Caso contrário, deixe false. NUNCA invente produto/cor/lote/preço fora "
                + "do catálogo. ANTES da tag, escreva a confirmação humana normal (\"Confirmado: 5 "
                + "novelos Lã Merino (Azul / lote L2024-A), total R$ X, entrega na Rua Y 🧶\"). NÃO "
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
        sb.append("Avise a cliente que o pedido ficará AGUARDANDO confirmação da loja.\n");
        // Onda 1 (backlog #5): cupom — validação/recálculo é do sistema.
        sb.append("Se a cliente informar um CUPOM de desconto, registre o código no campo \"cupom\" "
            + "da tag (omita se não houver) — quem valida e recalcula é o sistema; NUNCA invente "
            + "desconto (cupom inválido: o pedido sai sem o desconto).\n");
        // Onda 1 (backlog #1): lista de espera de dye lot.
        sb.append("LISTA DE ESPERA: se a cliente quer uma variante ESGOTADA (ou mais novelos de um "
            + "lote do que há em estoque), ofereça avisá-la quando chegar. Se ela aceitar, emita "
            + "(linha própria, sem markdown): <lista_espera_las>{\"variant_id\":\"UUID_DA_VARIANTE\","
            + "\"any_lot\":false,\"qty\":N}</lista_espera_las> — \"any_lot\":true quando QUALQUER lote "
            + "da cor servir; \"qty\" é a quantidade desejada (omita se não souber). NUNCA prometa "
            + "data de reposição — o aviso sai quando o estoque chegar de fato.\n");

        // Onda 1 (backlog #2): calculadora de rendimento — só do que o tenant cadastrou.
        appendYieldReference(sb, companyId);
        sb.append("\n");

        return sb.toString();
    }

    /** Bloco da calculadora de novelos (onda 1, backlog #2) — SEMPRE estimativa, nunca invenção. */
    private void appendYieldReference(StringBuilder sb, UUID companyId) {
        record Yield(String pieceType, String yarnSpec, int skeins, String notes) {}
        List<Yield> refs = jdbcTemplate.query(
            "select piece_type, yarn_spec, skeins, notes from las_yield_reference "
                + "where company_id = ? and active = true order by piece_type",
            (rs, rn) -> new Yield(rs.getString("piece_type"), rs.getString("yarn_spec"),
                rs.getInt("skeins"), rs.getString("notes")),
            companyId);
        if (refs.isEmpty()) {
            sb.append("CALCULADORA DE NOVELOS: a loja NÃO cadastrou referências de rendimento — se a "
                + "cliente perguntar quantos novelos precisa, diga que não tem a estimativa e sugira "
                + "confirmar com a loja. NUNCA invente dimensionamento.\n");
            return;
        }
        sb.append("CALCULADORA DE NOVELOS (referências da loja — use SEMPRE como ESTIMATIVA, ex.: "
            + "\"em média X novelos\"):\n");
        for (Yield y : refs) {
            sb.append("- ").append(y.pieceType());
            if (y.yarnSpec() != null && !y.yarnSpec().isBlank()) {
                sb.append(" · fio ").append(y.yarnSpec());
            }
            sb.append(" · ~").append(y.skeins()).append(" novelos");
            if (y.notes() != null && !y.notes().isBlank()) {
                sb.append(" (").append(y.notes()).append(")");
            }
            sb.append("\n");
        }
        sb.append("Peça que NÃO está na lista: diga que não tem a estimativa e sugira confirmar com a "
            + "loja. Ao fechar com estimativa, monte a quantidade COMPLETA no MESMO lote (amarra com "
            + "same_lot_guaranteed quando a cliente exigir).\n");
    }

    /**
     * Lista as variantes available=true do produto, uma linha com o variant_id + [cor / dye_lot] +
     * preço (variante ou base) + estoque (N em estoque / esgotado): {@code variant_id [color / dye_lot]
     * R$ p (N em estoque) | ...}.
     */
    private void appendVariants(StringBuilder sb, LasProduct p) {
        List<LasVariant> variants = p.variants();
        if (variants == null || variants.isEmpty()) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (LasVariant v : variants) {
            if (!v.available()) {
                continue;
            }
            if (line.length() > 0) {
                line.append(" | ");
            }
            int price = v.priceCents() != null ? v.priceCents() : p.basePriceCents();
            String stock = v.stockQty() > 0 ? (v.stockQty() + " em estoque") : "esgotado";
            line.append(v.id()).append(" [").append(v.color()).append(" / ").append(v.dyeLot()).append("] ")
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
