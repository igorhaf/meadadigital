package com.meada.profiles.pizzaria.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.pizzaria.menu.PizzariaMenuItem;
import com.meada.profiles.pizzaria.menu.PizzariaMenuItemRepository;
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
 * Extrai a tag {@code <pedido_pizza>{...}</pedido_pizza>} da resposta da IA, valida os itens
 * contra o cardápio do tenant e cria o pedido (camada 8.6). Clone do chassi comida + a ESCAPADA
 * meio-a-meio (parse das frações/sabores por item).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: a API trata os dois como
 * mutuamente exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em texto
 * livre; aqui parseamos via regex.
 *
 * <p>Cada item da tag tem DOIS modos:
 * <ul>
 *   <li>Item simples (bebida/sobremesa/borda/combo): {@code item_id} + {@code options} (modifiers).</li>
 *   <li>Pizza meio-a-meio/inteira: {@code flavors} (array de UUIDs de sabores, 1=inteira, 2=meio-a-
 *       meio) + {@code options}. O {@code item_id} é ignorado (o backend escolhe o sabor principal
 *       pela REGRA DO MAIOR VALOR).</li>
 * </ul>
 *
 * <p>O {@code total_cents} do JSON é DESCARTADO — o repositório recalcula (MAX dos sabores + Σ deltas
 * dos modifiers; defesa contra a IA chutar total). Sabor/opção fantasma → o repo lança
 * {@code InvalidFlavorException}/{@code InvalidOptionException} e o catch devolve {@link
 * Optional#empty()} (a mensagem da IA segue normal, sem pedido).
 */
@Component
public class PedidoPizzaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoPizzaConfirmHandler.class);

    // <pedido_pizza> ... </pedido_pizza> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_pizza>\\s*(\\{.*?\\})\\s*</pedido_pizza>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PizzariaMenuItemRepository menuRepository;
    private final PizzariaOrderService orderService;

    public PedidoPizzaConfirmHandler(ObjectMapper objectMapper, PizzariaMenuItemRepository menuRepository,
                                      PizzariaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.menuRepository = menuRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_pizza>...</pedido_pizza>} do texto (para não enviá-la ao cliente). */
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
    public Optional<PizzariaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("pizzaria: tag <pedido_pizza> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String endereco = root.path("endereco").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("pizzaria: tag <pedido_pizza> sem endereço p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("pizzaria: tag <pedido_pizza> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item. As opções/sabores NÃO têm validação fina aqui (passam adiante; o repo
        // recusa opção/sabor fantasma na criação) — mas o formato (UUID, qtd, presença) é checado.
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            int qtd = itemNode.path("qtd").asInt(0);
            if (qtd <= 0) {
                log.warn("pizzaria: item com qtd inválida na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }

            // Parse das opções (modifiers Tamanho/Borda; opcional — item sem opção → lista vazia).
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
                        log.warn("pizzaria: option_id não-UUID '{}' na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }

            // ESCAPADA meio-a-meio: se há flavors[], a linha é uma PIZZA (item_id ignorado; o sabor
            // principal — e o preço pela regra do MAIOR VALOR — é resolvido no repo).
            JsonNode flavorsNode = itemNode.path("flavors");
            if (flavorsNode.isArray() && !flavorsNode.isEmpty()) {
                List<UUID> flavorIds = new ArrayList<>();
                for (JsonNode flavorNode : flavorsNode) {
                    String rawFlavor = flavorNode.asText(null);
                    if (rawFlavor == null || rawFlavor.isBlank()) {
                        continue;
                    }
                    UUID flavorId;
                    try {
                        flavorId = UUID.fromString(rawFlavor);
                    } catch (IllegalArgumentException e) {
                        log.warn("pizzaria: flavor_id não-UUID '{}' na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                            rawFlavor, conversationId);
                        return Optional.empty();
                    }
                    Optional<PizzariaMenuItem> flavor = menuRepository.findById(companyId, flavorId);
                    if (flavor.isEmpty() || !flavor.get().available()) {
                        log.warn("pizzaria: sabor {} inexistente/indisponível na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                            flavorId, conversationId);
                        return Optional.empty();
                    }
                    flavorIds.add(flavorId);
                }
                if (flavorIds.isEmpty()) {
                    log.warn("pizzaria: item-pizza sem sabor válido na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                        conversationId);
                    return Optional.empty();
                }
                lines.add(new OrderLineInput(null, qtd, optionIds, flavorIds));
                continue;
            }

            // Item simples: precisa de item_id válido + disponível.
            String rawId = itemNode.path("item_id").asText(null);
            if (rawId == null) {
                log.warn("pizzaria: item sem item_id nem flavors na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("pizzaria: item_id não-UUID '{}' na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<PizzariaMenuItem> menuItem = menuRepository.findById(companyId, itemId);
            if (menuItem.isEmpty() || !menuItem.get().available()) {
                log.warn("pizzaria: item {} inexistente/indisponível na tag <pedido_pizza> p/ conversa {} — pedido não criado",
                    itemId, conversationId);
                return Optional.empty();
            }
            lines.add(new OrderLineInput(itemId, qtd, optionIds, List.of()));
        }

        // Cupom (backlog #1, opcional): só o CÓDIGO viaja na tag — quem valida e calcula é o backend
        // (cupom inválido não aborta; o pedido sai sem o desconto).
        String couponCode = root.path("cupom").asText(null);
        if (couponCode != null && couponCode.isBlank()) {
            couponCode = null;
        }

        try {
            PizzariaOrder order = orderService.create(companyId, conversationId, contactId,
                endereco.strip(), lines, couponCode, null);
            log.info("pizzaria: pedido {} criado p/ conversa {} ({} itens, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException (opção fantasma) e IllegalArgumentException (sem linha válida).
            log.warn("pizzaria: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
