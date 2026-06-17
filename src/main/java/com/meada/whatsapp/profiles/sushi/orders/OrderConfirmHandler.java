package com.meada.whatsapp.profiles.sushi.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuItem;
import com.meada.whatsapp.profiles.sushi.menu.SushiMenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.UUID;

/**
 * Extrai a tag {@code <pedido>{...}</pedido>} da resposta da IA, valida os itens contra o
 * cardápio do tenant e cria o pedido (camada 7.1, decisões 2 e 4).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: a API trata os dois como
 * mutuamente exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em texto
 * livre; aqui parseamos via regex.
 *
 * <p>O {@code total_cents} do JSON é DESCARTADO — o {@link SushiOrderService}/repository recalcula
 * a partir do cardápio (defesa contra a IA chutar total). Se algum item_id não existe/não está
 * disponível, retorna {@link Optional#empty()} (a mensagem da IA segue normal, sem pedido criado —
 * loga warn).
 */
@Component
public class OrderConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmHandler.class);

    // <pedido> ... </pedido> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile("<pedido>\\s*(\\{.*?\\})\\s*</pedido>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final SushiMenuItemRepository menuRepository;
    private final SushiOrderService orderService;

    public OrderConfirmHandler(ObjectMapper objectMapper, SushiMenuItemRepository menuRepository,
                               SushiOrderService orderService) {
        this.objectMapper = objectMapper;
        this.menuRepository = menuRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido>...</pedido>} do texto (para não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, nenhum item válido (item_id inexistente ou indisponível), ou endereço ausente.
     */
    public Optional<SushiOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("sushi: tag <pedido> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String endereco = root.path("endereco").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("sushi: tag <pedido> sem endereço p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("sushi: tag <pedido> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item: existe no cardápio do tenant E está disponível.
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("item_id").asText(null);
            int qtd = itemNode.path("qtd").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("sushi: item inválido (id/qtd) na tag <pedido> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("sushi: item_id não-UUID '{}' na tag <pedido> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<SushiMenuItem> menuItem = menuRepository.findById(companyId, itemId);
            if (menuItem.isEmpty() || !menuItem.get().available()) {
                // IA chutou um item que não existe/indisponível: aborta a criação (mensagem segue normal).
                log.warn("sushi: item {} inexistente/indisponível na tag <pedido> p/ conversa {} — pedido não criado",
                    itemId, conversationId);
                return Optional.empty();
            }
            lines.add(new OrderLineInput(itemId, qtd));
        }

        try {
            SushiOrder order = orderService.create(companyId, conversationId, contactId,
                endereco.strip(), lines, null);
            log.info("sushi: pedido {} criado p/ conversa {} ({} itens, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            log.warn("sushi: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
