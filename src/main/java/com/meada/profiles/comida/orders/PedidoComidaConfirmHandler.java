package com.meada.profiles.comida.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.comida.menu.ComidaMenuItem;
import com.meada.profiles.comida.menu.ComidaMenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <pedido_comida>{...}</pedido_comida>} da resposta da IA, valida os itens
 * contra o cardápio do tenant e cria o pedido (camada 8.4). Clone de
 * {@link com.meada.profiles.sushi.orders.OrderConfirmHandler} + a ESCAPADA 2 (parse das
 * opções por item).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: a API trata os dois como
 * mutuamente exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em texto
 * livre; aqui parseamos via regex.
 *
 * <p>O {@code total_cents} do JSON é DESCARTADO — o repositório recalcula (base + Σ deltas das
 * opções, defesa contra a IA chutar total). As OPÇÕES NÃO são validadas aqui — os optionIds passam
 * adiante; se o repo lançar {@code InvalidOptionException} ao criar (opção fantasma), o catch
 * devolve {@link Optional#empty()} (a mensagem da IA segue normal, sem pedido).
 */
@Component
public class PedidoComidaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoComidaConfirmHandler.class);

    // <pedido_comida> ... </pedido_comida> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_comida>\\s*(\\{.*?\\})\\s*</pedido_comida>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ComidaMenuItemRepository menuRepository;
    private final ComidaOrderService orderService;

    public PedidoComidaConfirmHandler(ObjectMapper objectMapper, ComidaMenuItemRepository menuRepository,
                                      ComidaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.menuRepository = menuRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_comida>...</pedido_comida>} do texto (para não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, nenhum item válido (item_id inexistente ou indisponível), endereço ausente, ou
     * o repo recusa por opção inválida.
     */
    public Optional<ComidaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
                                                String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();   // conversa normal (carrinho em construção).
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("comida: tag <pedido_comida> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String endereco = root.path("endereco").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("comida: tag <pedido_comida> sem endereço p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("comida: tag <pedido_comida> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item: existe no cardápio do tenant E está disponível. As opções NÃO são
        // validadas aqui (passam adiante; o repo recusa opção fantasma na criação).
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("item_id").asText(null);
            int qtd = itemNode.path("qtd").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("comida: item inválido (id/qtd) na tag <pedido_comida> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("comida: item_id não-UUID '{}' na tag <pedido_comida> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<ComidaMenuItem> menuItem = menuRepository.findById(companyId, itemId);
            if (menuItem.isEmpty() || !menuItem.get().available()) {
                log.warn("comida: item {} inexistente/indisponível na tag <pedido_comida> p/ conversa {} — pedido não criado",
                    itemId, conversationId);
                return Optional.empty();
            }

            // Parse das opções (array de UUIDs em string; opcional — item sem opção → lista vazia).
            List<UUID> optionIds = new ArrayList<>();
            JsonNode optionsNode = itemNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode optNode : optionsNode) {
                    String rawOpt = optNode.asText(null);
                    if (rawOpt == null || rawOpt.isBlank()) {
                        continue;
                    }
                    try {
                        optionIds.add(UUID.fromString(rawOpt));
                    } catch (IllegalArgumentException e) {
                        log.warn("comida: option_id não-UUID '{}' na tag <pedido_comida> p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }
            lines.add(new OrderLineInput(itemId, qtd, optionIds));
        }

        // Cupom (onda 1 #1) e zona de entrega (onda 1 #8) — OPCIONAIS na tag; quem valida/calcula é o
        // backend (cupom inválido não aborta; zona inválida → taxa flat).
        String couponCode = root.path("cupom").asText(null);
        if (couponCode != null && couponCode.isBlank()) {
            couponCode = null;
        }
        UUID zoneId = null;
        String rawZone = root.path("zona_id").asText(null);
        if (rawZone != null && !rawZone.isBlank()) {
            try {
                zoneId = UUID.fromString(rawZone);
            } catch (IllegalArgumentException e) {
                log.warn("comida: zona_id não-UUID '{}' na tag <pedido_comida> p/ conversa {} — usando taxa flat",
                    rawZone, conversationId);
            }
        }

        try {
            ComidaOrder order = orderService.create(companyId, conversationId, contactId,
                endereco.strip(), lines, couponCode, zoneId, null);
            log.info("comida: pedido {} criado p/ conversa {} ({} itens, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException (opção fantasma) e IllegalArgumentException (sem linha válida).
            log.warn("comida: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
