package com.meada.profiles.padaria;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.padaria.menu.PadariaMenuItem;
import com.meada.profiles.padaria.menu.PadariaMenuItemRepository;
import com.meada.profiles.padaria.menu.PadariaMenuOption;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do PadariaBot (camada 8.8 / perfil padaria).
 * Clone de {@link com.meada.profiles.floricultura.FloriculturaCatalogCache} (Caffeine TTL
 * 60s) — {@link com.meada.profiles.padaria.menu.PadariaMenuService} chama {@link #invalidate}
 * ao mutar item/opção/config, então a IA vê a mudança na hora. Ignora o conversationId (o contexto é o
 * cardápio).
 *
 * <p>DIFERENÇAS da floricultura (as 2 escapadas + fulfillment): sob cada item, marca se é SOB ENCOMENDA
 * (made_to_order) com seu lead em dias; ensina a tag {@code <encomenda_padaria>} com fulfillment
 * (retirada/entrega), data CONDICIONAL (obrigatória só com item sob encomenda, respeitando o lead),
 * personalização de bolo (cake_message) e as opções por item:
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt; [SOB ENCOMENDA: antecedência N dia(s)]
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 */
@Component
public class PadariaMenuCache {

    private final PadariaMenuItemRepository menuRepository;
    private final PadariaConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public PadariaMenuCache(PadariaMenuItemRepository menuRepository,
                            PadariaConfigRepository configRepository) {
        this.menuRepository = menuRepository;
        this.configRepository = configRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de cardápio+config+instruções para o prompt, cacheado por company (TTL 60s). */
    public String menuSegment(UUID companyId) {
        return cache.get(companyId, this::buildSegment);
    }

    /** Invalida o cache de uma empresa (chamado pelo PadariaMenuService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<PadariaMenuItem> items = menuRepository.listByCompany(companyId, null, true);
        PadariaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (PadariaMenuItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(PadariaCategory.fromId(currentCategory)
                        .map(PadariaCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents()));
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                if (it.madeToOrder()) {
                    int lead = it.leadTimeDays() != null ? it.leadTimeDays() : config.leadTimeDaysDefault();
                    sb.append(" [SOB ENCOMENDA: antecedência ").append(lead).append(" dia(s)]");
                }
                if (it.allergens() != null && !it.allergens().isBlank()) {
                    sb.append(" (alérgenos: ").append(it.allergens().strip()).append(")");
                }
                sb.append("\n");
                appendOptions(sb, it.options());
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("A padaria vende PRONTA-ENTREGA (pão, salgado, doce de balcão — entrega/retirada na "
                + "hora) e itens SOB ENCOMENDA (bolo/torta — exigem ANTECEDÊNCIA). ANTES de fechar, você "
                + "PRECISA saber a FORMA: 'retirada' (o cliente busca na loja, sem endereço) ou 'entrega' "
                + "(exige o ENDEREÇO). Se o pedido tiver ALGUM item SOB ENCOMENDA, você PRECISA da DATA "
                + "(formato YYYY-MM-DD) que respeite a antecedência do item (a MAIOR entre os itens sob "
                + "encomenda) e o PERÍODO ('manha' ou 'tarde'); pedido só de pronta-entrega NÃO precisa de "
                + "data. Para bolo, você pode anotar a MENSAGEM DA PLACA (cake_message) por item. Quando o "
                + "cliente CONFIRMAR e você tiver TODOS os dados, sua ÚLTIMA mensagem deve TERMINAR com a "
                + "tag (em uma linha própria, sem markdown):\n")
            .append("<encomenda_padaria>{\"fulfillment\":\"retirada\",\"pickup_or_delivery_date\":\"YYYY-MM-DD\","
                + "\"delivery_period\":\"manha\",\"delivery_address\":\"... ou null\",\"items\":[{\"menu_item_id\":"
                + "\"UUID_EXATO_DO_CARDÁPIO\",\"quantity\":N,\"options\":[{\"option_id\":\"UUID_DA_OPCAO\"}],"
                + "\"cake_message\":\"texto da placa ou null\"}],\"notes\":\"obs ou vazio\"}</encomenda_padaria>\n")
            .append("Use os menu_item_id e option_id EXATOS do cardápio acima. Item sem opção → omita "
                + "\"options\"; item sem placa → cake_message null. Em 'retirada', delivery_address é null. "
                + "Em pedido só de pronta-entrega, pickup_or_delivery_date e delivery_period podem ser null. "
                + "ANTES de fechar, você PODE oferecer UMA ÚNICA sugestão de complemento do PRÓPRIO "
                + "cardápio (vela, refrigerante, docinhos, cartão) — sem insistir se o cliente "
                + "recusar. Se a encomenda tiver SINAL registrado pela padaria, informe o valor e que "
                + "a produção começa após a confirmação do sinal — você NUNCA confirma pagamento. "
                + "ANTES da tag, escreva a confirmação humana normal (\"Confirmado: 1 Bolo de Chocolate "
                + "(Grande), retirada dia 25/12 de manhã, placa 'Parabéns'. Total R$ X.\"). NÃO emita a tag "
                + "sem TODOS os dados. A data NÃO pode ser no passado nem antes da antecedência mínima.\n");

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
        sb.append("Avise o cliente que o pedido será enviado para confirmação da padaria.\n\n");

        return sb.toString();
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<PadariaMenuOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (PadariaMenuOption opt : options) {
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
