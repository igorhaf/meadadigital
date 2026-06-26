package com.meada.whatsapp.profiles.suplementos.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Extrai a tag {@code <pedido_suplementos>{...}</pedido_suplementos>} da resposta da IA, valida as
 * linhas e cria o pedido (camada 8.24). Clone do {@code PedidoAdegaConfirmHandler} (mesmos nomes de
 * método: {@code hasOrderTag}/{@code stripOrderTag}/{@code parseAndCreate}), adaptado pro chassi de
 * varejo: cada linha referencia uma VARIANTE (sabor×peso), e a ⭐ ESCAPADA é o estoque — uma variante
 * esgotada (qtd > estoque) ABORTA o pedido inteiro (o repo decrementa transacionalmente e lança
 * OutOfStockException; o catch devolve {@link Optional#empty()}).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: mutuamente exclusivos com o
 * responseSchema já usado no outbound). A IA emite a tag em texto livre; aqui parseamos via regex.
 *
 * <p>O {@code total_cents} do JSON (se vier) é DESCARTADO — o repositório recalcula (unit_price =
 * variant.price_cents; defesa contra a IA chutar total). SÓ ENTREGA: {@code delivery_address}
 * obrigatório (ausente → empty; o repo também reforça com AddressRequiredException).
 */
@Component
public class PedidoSuplementosConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoSuplementosConfirmHandler.class);

    // <pedido_suplementos> ... </pedido_suplementos> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_suplementos>\\s*(\\{.*?\\})\\s*</pedido_suplementos>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final SupOrderService orderService;

    public PedidoSuplementosConfirmHandler(ObjectMapper objectMapper, SupOrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_suplementos>...</pedido_suplementos>} do texto (p/ não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida as linhas e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, nenhuma linha válida (variant_id ausente/não-UUID/qtd<=0), endereço de entrega
     * ausente, variante inexistente/inativa, OU ⭐ esgotada (o repo lança OutOfStockException).
     */
    public Optional<SupOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("suplementos: tag <pedido_suplementos> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // SÓ ENTREGA nesta SM — endereço obrigatório.
        String endereco = root.path("delivery_address").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("suplementos: tag <pedido_suplementos> sem delivery_address p/ conversa {} — pedido não criado",
                conversationId);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("suplementos: tag <pedido_suplementos> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("variant_id").asText(null);
            int qtd = itemNode.path("qtd").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("suplementos: item inválido (variant_id/qtd) na tag p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID variantId;
            try {
                variantId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("suplementos: variant_id não-UUID '{}' na tag p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            lines.add(new OrderLineInput(variantId, qtd));
        }

        String notes = root.path("notes").isNull() ? null : root.path("notes").asText(null);
        try {
            SupOrder order = orderService.create(companyId, conversationId, contactId,
                endereco.strip(), lines, notes);
            log.info("suplementos: pedido {} criado p/ conversa {} ({} linhas, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui OutOfStockException (esgotado), AddressRequiredException, variante inexistente
            // (sem linha válida → IllegalArgumentException), etc. A mensagem da IA segue normal, sem pedido.
            log.warn("suplementos: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
