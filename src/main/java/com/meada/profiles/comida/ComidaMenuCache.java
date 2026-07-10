package com.meada.profiles.comida;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.comida.loyalty.ComidaLoyaltyConfig;
import com.meada.profiles.comida.loyalty.ComidaLoyaltyConfigRepository;
import com.meada.profiles.comida.menu.ComidaMenuItem;
import com.meada.profiles.comida.menu.ComidaMenuItemRepository;
import com.meada.profiles.comida.menu.ComidaMenuOption;
import com.meada.profiles.comida.orders.ComidaOrderRepository;
import com.meada.profiles.comida.zones.ComidaDeliveryZone;
import com.meada.profiles.comida.zones.ComidaDeliveryZoneRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do ComidaBot (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.SushiMenuCache} (Caffeine TTL 60s) — {@link ComidaMenuService}
 * chama {@link #invalidate} ao mutar item/opção/config, então a IA vê a mudança na hora.
 *
 * <p>DIFERENÇA do sushi (ESCAPADA 2): sob cada item, lista os grupos de opção e seus deltas com os
 * option_id EXATOS — a IA precisa deles para emitir a tag {@code <pedido_comida>}. Formato por item:
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt;
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 *
 * <p>ONDA 1 do backlog: keyed por {@code (companyId, contactId)} — além do cardápio, injeta as
 * ZONAS de entrega com id EXATO (#8), ensina o campo {@code cupom} da tag (#1), anuncia o PROGRESSO
 * DA FIDELIDADE do contato (#2 — "faltam N pedidos"), oferece o ENDEREÇO do último pedido (#10) e
 * autoriza UMA sugestão de upsell do PRÓPRIO cardápio (#4).
 */
@Component
public class ComidaMenuCache {

    private final ComidaMenuItemRepository menuRepository;
    private final ComidaConfigRepository configRepository;
    private final ComidaDeliveryZoneRepository zoneRepository;
    private final ComidaLoyaltyConfigRepository loyaltyRepository;
    private final ComidaOrderRepository orderRepository;
    private final Cache<String, String> cache;

    public ComidaMenuCache(ComidaMenuItemRepository menuRepository,
                           ComidaConfigRepository configRepository,
                           ComidaDeliveryZoneRepository zoneRepository,
                           ComidaLoyaltyConfigRepository loyaltyRepository,
                           ComidaOrderRepository orderRepository) {
        this.menuRepository = menuRepository;
        this.configRepository = configRepository;
        this.zoneRepository = zoneRepository;
        this.loyaltyRepository = loyaltyRepository;
        this.orderRepository = orderRepository;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /** Bloco de cardápio+config+instruções para o prompt, cacheado por (company, contato) (TTL 60s). */
    public String menuSegment(UUID companyId, UUID contactId) {
        String key = companyId + ":" + (contactId == null ? "none" : contactId.toString());
        return cache.get(key, k -> buildSegment(companyId, contactId));
    }

    /** Compatibilidade: sem contato identificado (POSTs internos/testes antigos). */
    public String menuSegment(UUID companyId) {
        return menuSegment(companyId, null);
    }

    /** Invalida o cache de uma empresa (chamado ao mutar item/opção/config/zona/cupom/fidelidade). */
    public void invalidate(UUID companyId) {
        String prefix = companyId + ":";
        cache.asMap().keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        List<ComidaMenuItem> items = menuRepository.listByCompany(companyId, null, true);
        ComidaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (ComidaMenuItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(ComidaCategory.fromId(currentCategory)
                        .map(ComidaCategory::label).orElse(currentCategory)).append("]\n");
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
            .append("<pedido_comida>{\"items\":[{\"item_id\":\"UUID_EXATO_DO_CARDÁPIO\",\"qtd\":N,"
                + "\"options\":[\"UUID_DA_OPCAO\"]}],\"fulfillment\":\"entrega\",\"endereco\":\"...\","
                + "\"cupom\":\"CODIGO ou omitir\","
                + "\"zona_id\":\"UUID da zona ou omitir\",\"total_cents\":NNN}"
                + "</pedido_comida>\n")
            .append("RETIRADA NO BALCÃO: pergunte \"entrega ou retirada?\" no fechamento. "
                + "\"fulfillment\" é \"entrega\" (exige endereço; soma a taxa) ou \"retirada\" "
                + "(SEM taxa; endereço dispensado — o cliente busca no balcão).\n")
            .append("CUPOM: se o cliente informar um código de cupom, inclua-o no campo cupom — quem "
                + "valida e calcula o desconto é o SISTEMA; você NUNCA promete nem calcula desconto por "
                + "conta própria (se o cupom for inválido, o pedido sai sem desconto).\n")
            .append("Cada item pode ter \"options\" (lista de UUIDs das opções escolhidas dos grupos "
                + "acima); item sem opção → omita \"options\" ou use lista vazia. Use os item_id e "
                + "option_id EXATOS do cardápio acima. ANTES da tag, escreva a confirmação humana "
                + "normal (\"Confirmado: 1 X-Bacon (Grande, +Bacon) + 1 Refri, total R$ X, entrega "
                + "na Rua Y.\"). NÃO emita a tag enquanto o cliente ainda monta o pedido — só na "
                + "confirmação final COM endereço.\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total).\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise o cliente se o pedido ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        // Onda 2 (backlog #9): janela do delivery — a IA avisa fora do horário e NÃO fecha pedido.
        if (config.opensAt() != null && config.closesAt() != null) {
            sb.append("HORÁRIO DO DELIVERY: ").append(config.opensAt().toString().substring(0, 5))
                .append(" às ").append(config.closesAt().toString().substring(0, 5))
                .append(". FORA desse horário, avise que a cozinha está fechada, informe quando abre "
                    + "e NÃO emita a tag de pedido (anote o interesse e convide a voltar no horário).\n");
        }
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents()).append("\n");

        // ZONAS de entrega (onda 1 #8) — a taxa flat acima vira fallback quando há zona casada.
        List<ComidaDeliveryZone> zones = zoneRepository.listByCompany(companyId, true);
        if (!zones.isEmpty()) {
            sb.append("ZONAS DE ENTREGA (pergunte o BAIRRO do cliente e use o zona_id EXATO na tag; "
                + "bairro fora das zonas → omita zona_id e vale a taxa padrão):\n");
            for (ComidaDeliveryZone z : zones) {
                sb.append("- ").append(z.id()).append(" · ").append(z.name())
                    .append(": taxa R$ ").append(formatBrl(z.feeCents())).append("\n");
            }
        }

        // FIDELIDADE (onda 1 #2) — progresso anunciável; quem aplica o desconto é o SISTEMA.
        ComidaLoyaltyConfig loyalty = loyaltyRepository.findByCompany(companyId);
        if (loyalty.enabled() && contactId != null) {
            long delivered = orderRepository.countDeliveredForContact(companyId, contactId);
            long threshold = loyalty.thresholdOrders();
            long intoCycle = delivered % threshold;
            String reward = "percent".equals(loyalty.rewardKind())
                ? loyalty.rewardValue() + "%"
                : "R$ " + formatBrl(loyalty.rewardValue());
            if (delivered > 0 && intoCycle == 0) {
                sb.append("FIDELIDADE: o PRÓXIMO pedido deste cliente ganha o desconto de ").append(reward)
                    .append(" automaticamente (o sistema aplica — você pode anunciar a boa notícia, "
                        + "sem calcular o total com desconto).\n");
            } else {
                sb.append("FIDELIDADE: cliente tem ").append(delivered)
                    .append(" pedido(s) entregue(s); a cada ").append(threshold)
                    .append(" o próximo sai com ").append(reward)
                    .append(" de desconto — faltam ").append(threshold - intoCycle)
                    .append(" pedido(s). Você pode mencionar o progresso; NUNCA aplica o desconto "
                        + "por conta própria.\n");
            }
        }

        // ENDEREÇO SALVO (onda 1 #10) — reuso do último endereço em vez de pedir pra digitar.
        if (contactId != null) {
            orderRepository.findLastAddressForContact(companyId, contactId).ifPresent(addr ->
                sb.append("ENDEREÇO DO ÚLTIMO PEDIDO deste cliente: \"").append(addr)
                    .append("\" — pergunte se a entrega é no MESMO endereço antes de pedir pra digitar "
                        + "de novo (se confirmar, use-o no campo endereco).\n"));
        }

        // UPSELL (onda 1 #4) — UMA sugestão do PRÓPRIO cardápio, sem insistir.
        sb.append("UPSELL: ao fechar o pedido, você PODE sugerir NO MÁXIMO UMA VEZ um complemento do "
            + "PRÓPRIO cardápio acima (bebida, sobremesa ou adicional que combine) — sem insistir se o "
            + "cliente recusar e sem sugerir nada fora do cardápio.\n");
        sb.append("Avise o cliente que o pedido será enviado para confirmação do restaurante.\n\n");

        return sb.toString();
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<ComidaMenuOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (ComidaMenuOption opt : options) {
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
