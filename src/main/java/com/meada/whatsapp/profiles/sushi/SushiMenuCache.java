package com.meada.whatsapp.profiles.sushi;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuItem;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuItemRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do SushiBot (camada 7.1). Ler o cardápio
 * a cada mensagem da IA seria caro; cacheamos o BLOCO DE TEXTO já formatado por company, com TTL
 * de 60s. {@link SushiMenuService} chama {@link #invalidate} ao gravar/atualizar/excluir um item
 * ou a config — então a IA vê a mudança na hora, sem esperar o TTL.
 *
 * <p>O conteúdo é só itens {@code available=true} (o que a IA pode oferecer), agrupados por
 * categoria, com o item_id (a IA precisa dele para emitir a tag &lt;pedido&gt;) + a config
 * (taxa/mínimo) + as instruções de pedido.
 */
@Component
public class SushiMenuCache {

    private final SushiMenuItemRepository menuRepository;
    private final SushiRestaurantConfigRepository configRepository;
    private final Cache<UUID, String> cache;

    public SushiMenuCache(SushiMenuItemRepository menuRepository,
                          SushiRestaurantConfigRepository configRepository) {
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

    /** Invalida o cache de uma empresa (chamado pelo SushiMenuService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.invalidate(companyId);
    }

    private String buildSegment(UUID companyId) {
        List<SushiMenuItem> items = menuRepository.listByCompany(companyId, null, true);
        SushiRestaurantConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (SushiMenuItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(SushiCategory.fromId(currentCategory)
                        .map(SushiCategory::label).orElse(currentCategory)).append("]\n");
                }
                sb.append("- ").append(it.id()).append(" · ").append(it.name())
                    .append(" · R$ ").append(formatBrl(it.priceCents()));
                if (it.description() != null && !it.description().isBlank()) {
                    sb.append(" · ").append(it.description().strip());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("INSTRUÇÕES DE PEDIDO:\n")
            .append("Quando o cliente CONFIRMAR o pedido (frases como \"pode mandar\", \"confirma\", "
                + "\"tá certo\", \"fechou\") E já tiver informado o endereço de entrega, sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<pedido>{\"items\":[{\"item_id\":\"UUID_EXATO_DO_CARDÁPIO\",\"qtd\":N}],"
                + "\"endereco\":\"...\",\"total_cents\":NNN}</pedido>\n")
            .append("Use os item_id EXATOS do cardápio acima. ANTES da tag, escreva a confirmação "
                + "humana normal (\"Confirmado: 2 Filadélfia + 1 California, total R$ X, entrega na "
                + "Rua Y. Já já tá saindo!\"). NÃO emita a tag enquanto o cliente ainda monta o "
                + "pedido — só na confirmação final COM endereço.\n");

        if (config.deliveryFeeCents() > 0) {
            sb.append("Taxa de entrega: R$ ").append(formatBrl(config.deliveryFeeCents()))
                .append(" (some ao total).\n");
        }
        if (config.minOrderCents() > 0) {
            sb.append("Pedido mínimo: R$ ").append(formatBrl(config.minOrderCents()))
                .append(" (avise o cliente se o pedido ficar abaixo, mas não recuse — apenas oriente).\n");
        }
        sb.append("CONFIG: delivery_fee_cents=").append(config.deliveryFeeCents())
            .append(", min_order_cents=").append(config.minOrderCents()).append("\n\n");

        return sb.toString();
    }

    private static String formatBrl(int cents) {
        return String.format("%d,%02d", cents / 100, cents % 100);
    }
}
