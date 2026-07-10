package com.meada.profiles.floricultura;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItem;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItemRepository;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogOption;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cache do bloco de cardápio+config injetado no prompt do FloriculturaBot (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.SushiCatalogCache} (Caffeine TTL 60s) — {@link FloriculturaCatalogService}
 * chama {@link #invalidate} ao mutar item/opção/config, então a IA vê a mudança na hora.
 *
 * <p>DIFERENÇA do sushi (ESCAPADA 2): sob cada item, lista os grupos de opção e seus deltas com os
 * option_id EXATOS — a IA precisa deles para emitir a tag {@code <pedido_flor>}. Formato por item:
 * <pre>
 * - &lt;item_id&gt; · &lt;name&gt; · R$ &lt;base&gt;
 *     [&lt;group_label&gt;] &lt;opt_id&gt; &lt;option_label&gt; (+R$ &lt;delta&gt;) | ...
 * </pre>
 */
@Component
public class FloriculturaCatalogCache {

    private final FloriculturaCatalogItemRepository catalogRepository;
    private final FloriculturaConfigRepository configRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final Cache<String, String> cache;

    public FloriculturaCatalogCache(FloriculturaCatalogItemRepository catalogRepository,
                           FloriculturaConfigRepository configRepository,
                           org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.catalogRepository = catalogRepository;
        this.configRepository = configRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(500)
            .build();
    }

    /**
     * Bloco de cardápio+config+instruções para o prompt, cacheado por company:contact (TTL 60s).
     * O contato entra pra injetar o HISTÓRICO de destinatários (onda 1, backlog #3 — recompra).
     */
    public String catalogSegment(UUID companyId, UUID contactId) {
        return cache.get(companyId + ":" + contactId, k -> buildSegment(companyId, contactId));
    }

    /** Invalida o cache de uma empresa (chamado pelo FloriculturaCatalogService ao mutar). */
    public void invalidate(UUID companyId) {
        cache.asMap().keySet().removeIf(k -> k.startsWith(companyId + ":"));
    }

    private String buildSegment(UUID companyId, UUID contactId) {
        List<FloriculturaCatalogItem> items = catalogRepository.listByCompany(companyId, null, true);
        FloriculturaConfig config = configRepository.findByCompany(companyId);

        StringBuilder sb = new StringBuilder();
        if (items.isEmpty()) {
            sb.append("CARDÁPIO DISPONÍVEL HOJE: (nenhum item disponível no momento — informe o "
                + "cliente que o cardápio está indisponível e ofereça avisá-lo quando voltar.)\n\n");
        } else {
            sb.append("CARDÁPIO DISPONÍVEL HOJE:\n");
            String currentCategory = null;
            for (FloriculturaCatalogItem it : items) {
                if (!it.category().equals(currentCategory)) {
                    currentCategory = it.category();
                    sb.append("[").append(FloriculturaCategory.fromId(currentCategory)
                        .map(FloriculturaCategory::label).orElse(currentCategory)).append("]\n");
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
            .append("Flor é presente AGENDADO pra OUTRA pessoa. ANTES de fechar, você PRECISA ter: os "
                + "itens, o ENDEREÇO de entrega, a DATA de entrega (formato YYYY-MM-DD, hoje ou futura), "
                + "o PERÍODO ('manha' ou 'tarde'), o NOME de quem vai RECEBER, e (opcional) a mensagem do "
                + "CARTÃO. Quando o cliente CONFIRMAR o pedido E você tiver TODOS esses dados, sua ÚLTIMA "
                + "mensagem deve TERMINAR com a tag (em uma linha própria, sem markdown):\n")
            .append("<pedido_flor>{\"items\":[{\"item_id\":\"UUID_EXATO_DO_CATÁLOGO\",\"qtd\":N,"
                + "\"options\":[\"UUID_DA_OPCAO\"]}],\"endereco\":\"...\",\"data_entrega\":\"YYYY-MM-DD\","
                + "\"periodo\":\"manha\",\"destinatario\":\"Nome de quem recebe\",\"cartao\":\"mensagem ou vazio\","
                + "\"cupom\":\"CODIGO_SE_HOUVER\",\"anonimo\":false,"
                + "\"total_cents\":NNN}</pedido_flor>\n")
            .append("Cada item pode ter \"options\" (UUIDs das opções de cor/tamanho escolhidas); item "
                + "sem opção → omita \"options\". Use os item_id e option_id EXATOS do catálogo acima. "
                + "ANTES da tag, escreva a confirmação humana normal (\"Confirmado: 1 Buquê de Rosas "
                + "(Grande), entrega 25/12 de manhã para Maria, na Rua Y, com cartão. Total R$ X.\"). NÃO "
                + "emita a tag sem TODOS os dados (itens+endereço+data+período+destinatário) — só na "
                + "confirmação final completa. A data NÃO pode ser no passado.\n");

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
        sb.append("Avise o cliente que o pedido será enviado para confirmação da loja.\n");
        // Onda 1 (backlog #7): cupom — validação/recálculo é do sistema.
        sb.append("Se o cliente informar um CUPOM de desconto, registre o código no campo \"cupom\" "
            + "da tag (omita se não houver) — quem valida e recalcula é o sistema; NUNCA invente "
            + "desconto (cupom inválido: o pedido sai sem o desconto).\n");
        // Onda 1 (backlog #13): presente surpresa.
        sb.append("PRESENTE SURPRESA: se o cliente quiser que a entrega NÃO revele quem enviou, "
            + "ponha \"anonimo\":true na tag (e o cartão sem assinatura). Confirme com o cliente "
            + "que o presente será entregue sem identificar o remetente.\n");
        // Onda 1 (backlog #4): upsell controlado — só itens que o tenant marcou.
        List<FloriculturaCatalogItem> suggestibles = items.stream()
            .filter(FloriculturaCatalogItem::suggestible).toList();
        if (!suggestibles.isEmpty()) {
            sb.append("UPSELL: ao fechar o carrinho, você PODE sugerir UM destes adicionais quando "
                + "combinar com a ocasião (sem insistir; preço SEMPRE do catálogo):\n");
            for (FloriculturaCatalogItem it : suggestibles) {
                sb.append("- ").append(it.name()).append(" (R$ ").append(formatBrl(it.priceCents()))
                    .append(")\n");
            }
        }
        sb.append("\n");

        // Onda 1 (backlog #3): recompra de 1 clique — histórico de destinatários do contato.
        appendRecentOrders(sb, companyId, contactId);

        return sb.toString();
    }

    /**
     * Histórico de pedidos do contato (onda 1, backlog #3): flor vai pras MESMAS pessoas — a IA
     * oferece "repetir o buquê da Ana" remontando a tag a partir do pedido anterior.
     */
    private void appendRecentOrders(StringBuilder sb, UUID companyId, UUID contactId) {
        if (contactId == null) {
            return;
        }
        record Past(String recipient, String address, String items, java.sql.Date date) {}
        List<Past> past = jdbcTemplate.query(
            "select o.recipient_name, o.delivery_address, o.delivery_date, "
                + "(select string_agg(i.qtd || 'x ' || i.item_name_snapshot, ', ') "
                + "  from floricultura_order_items i where i.order_id = o.id) as items "
                + "from floricultura_orders o "
                + "where o.company_id = ? and o.contact_id = ? and o.status = 'entregue' "
                + "order by o.created_at desc limit 3",
            (rs, rn) -> new Past(rs.getString("recipient_name"), rs.getString("delivery_address"),
                rs.getString("items"), rs.getDate("delivery_date")),
            companyId, contactId);
        if (past.isEmpty()) {
            return;
        }
        sb.append("PEDIDOS ANTERIORES DESTE CLIENTE (ofereça REPETIR quando fizer sentido — mesmo "
            + "destinatário/endereço, nova data; monte a tag normalmente):\n");
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (Past pOrder : past) {
            sb.append("- para ").append(pOrder.recipient()).append(" em ")
                .append(pOrder.date().toLocalDate().format(fmt))
                .append(": ").append(pOrder.items() == null ? "(itens)" : pOrder.items())
                .append(" · endereço: ").append(pOrder.address()).append("\n");
        }
        sb.append("\n");
    }

    /**
     * Lista as opções available=true do item, agrupadas por group_label (ordem de aparição já vem
     * por sort_order do repositório), uma linha por grupo: {@code [grupo] opt_id label (+R$ delta) | ...}.
     */
    private void appendOptions(StringBuilder sb, List<FloriculturaCatalogOption> options) {
        if (options == null || options.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byGroup = new LinkedHashMap<>();
        for (FloriculturaCatalogOption opt : options) {
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
