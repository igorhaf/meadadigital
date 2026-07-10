package com.meada.profiles.pizzaria;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.pizzaria.menu.PizzariaMenuItem;
import com.meada.profiles.pizzaria.menu.PizzariaMenuItemRepository;
import com.meada.profiles.pizzaria.menu.PizzariaMenuOption;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do PizzariaBot (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.SushiMenuCache} (Caffeine TTL 60s) — {@link PizzariaMenuService}
 * chama {@link #invalidate} ao mutar item/opção/config, então a IA vê a mudança na hora.
 *
 * <p>DIFERENÇA do sushi (ESCAPADA 2): sob cada item, lista os grupos de opção e seus deltas com os
 * option_id EXATOS — a IA precisa deles para emitir a tag {@code <pedido_pizza>}. Formato por item:
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt;
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 */
@Component
public class PizzariaMenuCache {

    private final PizzariaMenuItemRepository menuRepository;
    private final PizzariaConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public PizzariaMenuCache(PizzariaMenuItemRepository menuRepository,
                           PizzariaConfigRepository configRepository) {
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

    /** Invalida o cache de uma empresa (chamado pelo PizzariaMenuService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<PizzariaMenuItem> items = menuRepository.listByCompany(companyId, null, true);
        PizzariaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (PizzariaMenuItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(PizzaCategory.fromId(currentCategory)
                        .map(PizzaCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents()));
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                sb.append("\n");
                appendOptions(sb, it.options());
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Quando o cliente CONFIRMAR o pedido (frases como \"pode mandar\", \"confirma\", "
                + "\"tá certo\", \"fechou\") E já tiver informado o endereço de entrega, sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<pedido_pizza>{\"items\":[{\"item_id\":\"UUID_EXATO_DO_CARDÁPIO\",\"qtd\":N,"
                + "\"options\":[\"UUID_DA_OPCAO\"]}],\"endereco\":\"...\","
                + "\"cupom\":\"CODIGO_SE_HOUVER\",\"total_cents\":NNN}"
                + "</pedido_pizza>\n")
            .append("Cada item simples (bebida, sobremesa, borda, combo) usa \"item_id\" + \"options\" "
                + "(lista de UUIDs das opções escolhidas dos grupos acima); item sem opção → omita "
                + "\"options\" ou use lista vazia.\n")
            .append("PIZZA MEIO-A-MEIO: para uma pizza, NÃO use \"item_id\" — use \"flavors\" (lista "
                + "dos UUIDs dos SABORES das frações: 1 sabor = pizza inteira, 2 sabores = meio-a-meio). "
                + "Cada sabor é um item-pizza do cardápio acima. Ex.: pizza meio Portuguesa meio Quatro "
                + "Queijos, tamanho G, borda recheada → {\"flavors\":[\"UUID_PORTUGUESA\",\"UUID_QUATRO_"
                + "QUEIJOS\"],\"options\":[\"UUID_TAMANHO_G\",\"UUID_BORDA_RECHEADA\"],\"qtd\":1}. O preço "
                + "da pizza meio-a-meio é o do sabor MAIS CARO (regra da casa) + as opções; o sistema "
                + "calcula — NUNCA invente o total.\n")
            .append("Se o cliente informar um CUPOM de desconto, registre o código no campo \"cupom\" "
                + "(omita se não houver) — quem VALIDA o cupom, calcula a fidelidade e recalcula o "
                + "total é o sistema; NUNCA invente desconto nem prometa que o cupom vale (se for "
                + "inválido, o pedido sai sem o desconto). ANTES de fechar, você PODE oferecer UMA "
                + "ÚNICA sugestão de complemento do PRÓPRIO cardápio (borda recheada, bebida ou "
                + "sobremesa que combine com o carrinho) — no máximo uma vez, sem insistir se o "
                + "cliente recusar. ")
            .append("Use os item_id, option_id e flavor (UUIDs) EXATOS do cardápio acima. ANTES da tag, "
                + "escreva a confirmação humana normal (\"Confirmado: 1 pizza G meio Portuguesa/meio "
                + "Quatro Queijos com borda recheada + 1 Refri, total R$ X, entrega na Rua Y.\"). NÃO "
                + "emita a tag enquanto o cliente ainda monta o pedido — só na confirmação final COM "
                + "endereço.\n");

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
        sb.append("Avise o cliente que o pedido será enviado para confirmação do restaurante.\n\n");

        return sb.toString();
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<PizzariaMenuOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (PizzariaMenuOption opt : options) {
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
