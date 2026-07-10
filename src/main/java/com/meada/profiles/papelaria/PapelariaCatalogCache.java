package com.meada.profiles.papelaria;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogItem;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogItemRepository;
import com.meada.profiles.papelaria.catalog.PapelariaCatalogOption;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de catálogo+config injetado no prompt do PapelariaBot (camada 8.15 / perfil
 * papelaria). Clone de {@link com.meada.profiles.padaria.PadariaMenuCache} (camada 8.8,
 * Caffeine TTL 60s) — {@link com.meada.profiles.papelaria.catalog.PapelariaCatalogService}
 * chama {@link #invalidate} ao mutar item/opção/config, então a IA vê a mudança na hora. Ignora o
 * conversationId (o contexto é o catálogo).
 *
 * <p>ESCAPADAS desta SM: sob cada item, marca se é SOB ENCOMENDA (made_to_order) com seu lead em dias;
 * ensina a tag {@code <pedido_papelaria>} com fulfillment (retirada/entrega), data CONDICIONAL
 * (obrigatória só com item sob encomenda), TIRAGEM (quantity = quantas peças, ex.: 50/100/200), texto
 * personalizado (custom_text) e as opções por item; e ensina a 2ª tag {@code <aprovacao_arte>} para
 * o cliente APROVAR a arte de um pedido EXISTENTE em 'arte_aprovacao' (a IA nunca sobe a arte — ação
 * humana — só captura a aprovação):
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt;/un [SOB ENCOMENDA: antecedência N dia(s)]
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 */
@Component
public class PapelariaCatalogCache {

    private final PapelariaCatalogItemRepository catalogRepository;
    private final PapelariaConfigRepository configRepository;
    private final Cache<UUID, String> cache;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public PapelariaCatalogCache(PapelariaCatalogItemRepository catalogRepository,
                                 PapelariaConfigRepository configRepository,
                                  org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.catalogRepository = catalogRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Bloco de catálogo+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String catalogSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo PapelariaCatalogService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<PapelariaCatalogItem> items = catalogRepository.listByCompany(companyId, null, true);
        PapelariaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CATÁLOGO DISPONÍVEL: (nenhum item disponível no momento — informe o cliente que "
                + "o catálogo está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CATÁLOGO DISPONÍVEL:\n");
            String currentCategory = null;
            for (PapelariaCatalogItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(PapelariaCategory.fromId(currentCategory)
                        .map(PapelariaCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents())).append("/un");
                // Onda #2: faixas de tiragem — a IA apresenta o preço/un por quantidade.
                java.util.List<com.meada.profiles.papelaria.catalog.PapelariaItemTier> tiers =
                    jdbcTemplate.query(
                        "select min_qty, unit_price_cents from papelaria_item_tiers where item_id = ? order by min_qty",
                        (trs, trn) -> new com.meada.profiles.papelaria.catalog.PapelariaItemTier(
                            trs.getInt("min_qty"), trs.getInt("unit_price_cents")), it.id());
                if (!tiers.isEmpty()) {
                    sb.append(" · POR TIRAGEM:");
                    for (var t : tiers) {
                        sb.append(" ").append(t.minQty()).append("+ un = R$ ")
                            .append(formatBrl(t.unitPriceCents())).append("/un;");
                    }
                    sb.append(" (informe as faixas — tiragem maior sai mais barato por unidade)");
                }
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                if (it.madeToOrder()) {
                    int lead = it.leadTimeDays() != null ? it.leadTimeDays() : config.leadTimeDaysDefault();
                    sb.append(" [SOB ENCOMENDA: antecedência ").append(lead).append(" dia(s)]");
                }
                if (it.specs() != null && !it.specs().isBlank()) {
                    sb.append(" (specs: ").append(it.specs().strip()).append(")");
                }
                sb.append("\n");
                appendOptions(sb, it.options());
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("A papelaria vende PRONTA-ENTREGA (itens de balcão) e itens SOB ENCOMENDA (convites, "
                + "save the date, cartões personalizados — exigem ANTECEDÊNCIA). O preço de cada item é "
                + "UNITÁRIO; a TIRAGEM (quantity) é quantas peças o cliente quer (ex.: 50, 100, 200 "
                + "convites) e MULTIPLICA o valor. ANTES de fechar, você PRECISA saber a FORMA: 'retirada' "
                + "(o cliente busca na loja, sem endereço) ou 'entrega' (exige o ENDEREÇO). Se o pedido "
                + "tiver ALGUM item SOB ENCOMENDA, você PRECISA da DATA (formato YYYY-MM-DD) que respeite a "
                + "antecedência do item (a MAIOR entre os itens sob encomenda) e o PERÍODO ('manha' ou "
                + "'tarde'); pedido só de pronta-entrega NÃO precisa de data. Você pode anotar o TEXTO "
                + "PERSONALIZADO (custom_text) por item (ex.: os nomes dos noivos, a data do evento). Quando "
                + "o cliente CONFIRMAR e você tiver TODOS os dados, sua ÚLTIMA mensagem deve TERMINAR com a "
                + "tag (em uma linha própria, sem markdown):\n")
            .append("<pedido_papelaria>{\"fulfillment\":\"retirada\",\"pickup_or_delivery_date\":\"YYYY-MM-DD\","
                + "\"delivery_period\":\"manha\",\"delivery_address\":\"... ou null\",\"items\":[{\"catalog_item_id\":"
                + "\"UUID_EXATO_DO_CATÁLOGO\",\"quantity\":N,\"options\":[{\"option_id\":\"UUID_DA_OPCAO\"}],"
                + "\"custom_text\":\"texto personalizado ou null\"}],\"notes\":\"obs ou vazio\"}</pedido_papelaria>\n")
            .append("Use os catalog_item_id e option_id EXATOS do catálogo acima. quantity é a TIRAGEM (>= 1). "
                + "Item sem opção → omita \"options\"; item sem personalização → custom_text null. Em "
                + "'retirada', delivery_address é null. Em pedido só de pronta-entrega, "
                + "pickup_or_delivery_date e delivery_period podem ser null. ANTES da tag, escreva a "
                + "confirmação humana normal (\"Confirmado: 100 convites (papel perolado), entrega dia "
                + "20/12 de manhã. Total R$ X.\"). ANTES de fechar, você PODE oferecer UMA ÚNICA "
                + "sugestão de item complementar de OUTRA categoria do catálogo acima (convite → save "
                + "the date/tags/menu; bolo de festa → adesivos/embalagens) — sem insistir se o "
                + "cliente recusar. Se o pedido tiver SINAL registrado pela equipe, informe o valor e "
                + "que a produção começa após a confirmação do sinal — você NUNCA confirma pagamento. "
                + "NÃO emita a tag sem TODOS os dados. A data NÃO pode ser "
                + "no passado nem antes da antecedência mínima.\n")
            .append("PROVA DE ARTE: depois que a papelaria ACEITA o pedido, a equipe sobe a ARTE do layout "
                + "e o pedido entra em APROVAÇÃO DA ARTE. Quando o cliente APROVAR a arte na conversa, sua "
                + "ÚLTIMA mensagem deve TERMINAR com a tag (linha própria):\n")
            .append("<aprovacao_arte>{\"order_id\":\"UUID_DO_PEDIDO ou null\"}</aprovacao_arte>\n")
            .append("Use essa tag SÓ quando há um pedido aguardando aprovação da arte e o cliente disse que "
                + "APROVOU. Você NÃO sobe arte nem aceita/recusa pedido (isso é a equipe que faz no painel).\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total APENAS em 'entrega').\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise o cliente se o pedido ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents())
            .append(", lead_time_days_default=").append(config.leadTimeDaysDefault()).append("\n");
        sb.append("Avise o cliente que o pedido será enviado para confirmação da papelaria.\n\n");

        return sb.toString();
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<PapelariaCatalogOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (PapelariaCatalogOption opt : options) {
            if (!opt.available()) {
                continue;
            }
            StringBuilder line = byGroup.computeIfAbsent(opt.groupLabel(), g -> new StringBuilder());
            if (line.length() > 0) {
                line.append(" | ");
            }
            line.append(opt.id()).append(" ").append(opt.optionLabel())
                .append(" (+R$ ").append(formatBrl(opt.priceDeltaCents())).append(")");
        }
        for (Map.Entry<String, StringBuilder> e : byGroup.entrySet()) {
            sb.append("    [").append(e.getKey()).append("] ").append(e.getValue()).append("\n");
        }
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
