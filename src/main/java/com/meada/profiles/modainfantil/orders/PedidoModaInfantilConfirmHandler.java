package com.meada.profiles.modainfantil.orders;

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
 * Extrai a tag {@code <pedido_moda_infantil>{...}</pedido_moda_infantil>} da resposta da IA, valida as
 * linhas e cria o pedido (camada 8.22). Clone do
 * {@link com.meada.profiles.lingerie.orders.PedidoLingerieConfirmHandler} (chassi de varejo):
 * cada linha referencia uma VARIANTE (não item+opções), e o estoque — uma variante esgotada (qtd >
 * estoque) ABORTA o pedido inteiro (o repo decrementa transacionalmente e lança OutOfStockException; o
 * catch devolve {@link Optional#empty()}).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: mutuamente exclusivos com o
 * responseSchema já usado no outbound). A IA emite a tag em texto livre; aqui parseamos via regex.
 *
 * <p>O {@code total_cents} do JSON é DESCARTADO — o repositório recalcula (unit_price = variant.price
 * ?? product.base_price; defesa contra a IA chutar total). {@code fulfillment} é entrega (exige
 * endereço) ou retirada (sem endereço).
 */
@Component
public class PedidoModaInfantilConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoModaInfantilConfirmHandler.class);

    // <pedido_moda_infantil> ... </pedido_moda_infantil> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_moda_infantil>\\s*(\\{.*?\\})\\s*</pedido_moda_infantil>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ModaInfantilOrderService orderService;

    public PedidoModaInfantilConfirmHandler(ObjectMapper objectMapper, ModaInfantilOrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_moda_infantil>...</pedido_moda_infantil>} do texto (para não enviá-la à cliente). */
    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida as linhas e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, nenhuma linha válida (variant_id ausente/não-UUID/qtd<=0), entrega sem endereço,
     * variante inexistente/indisponível, OU esgotada (o repo lança OutOfStockException). retirada
     * dispensa endereço.
     */
    public Optional<ModaInfantilOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("moda_infantil: tag <pedido_moda_infantil> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // fulfillment: entrega (default) ou retirada. Qualquer outro valor → entrega (conservador).
        String fulfillment = root.path("fulfillment").asText("entrega");
        if (!"entrega".equals(fulfillment) && !"retirada".equals(fulfillment)) {
            fulfillment = "entrega";
        }

        String endereco = root.path("endereco").asText(null);
        if ("entrega".equals(fulfillment) && (endereco == null || endereco.isBlank())) {
            log.warn("moda_infantil: tag <pedido_moda_infantil> em ENTREGA sem endereço p/ conversa {} — pedido não criado",
                conversationId);
            return Optional.empty();
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("moda_infantil: tag <pedido_moda_infantil> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("variant_id").asText(null);
            int qtd = itemNode.path("qtd").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("moda_infantil: item inválido (variant_id/qtd) na tag p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID variantId;
            try {
                variantId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("moda_infantil: variant_id não-UUID '{}' na tag p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            lines.add(new OrderLineInput(variantId, qtd));
        }

        String address = "entrega".equals(fulfillment) && endereco != null ? endereco.strip() : null;
        try {
            // Cupom (onda 1, opcional): só o CÓDIGO viaja na tag — quem valida/calcula é o backend.
            String couponCode = root.path("cupom").asText(null);
            if (couponCode != null && couponCode.isBlank()) {
                couponCode = null;
            }
            ModaInfantilOrder order = orderService.create(companyId, conversationId, contactId,
                fulfillment, address, lines, couponCode, null);
            log.info("moda_infantil: pedido {} criado p/ conversa {} ({} linhas, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui OutOfStockException (esgotado), variante inexistente (sem linha válida →
            // IllegalArgumentException), etc. A mensagem da IA segue normal, sem pedido.
            log.warn("moda_infantil: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
