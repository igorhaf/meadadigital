package com.meada.profiles.floricultura.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItem;
import com.meada.profiles.floricultura.catalog.FloriculturaCatalogItemRepository;
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
 * Extrai a tag {@code <pedido_flor>{...}</pedido_flor>} da resposta da IA, valida os itens
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
public class PedidoFlorConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoFlorConfirmHandler.class);

    // <pedido_flor> ... </pedido_flor> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<pedido_flor>\\s*(\\{.*?\\})\\s*</pedido_flor>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final FloriculturaCatalogItemRepository catalogRepository;
    private final FloriculturaOrderService orderService;

    public PedidoFlorConfirmHandler(ObjectMapper objectMapper, FloriculturaCatalogItemRepository catalogRepository,
                                      FloriculturaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.catalogRepository = catalogRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_flor>...</pedido_flor>} do texto (para não enviá-la ao cliente). */
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
    public Optional<FloriculturaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("floricultura: tag <pedido_flor> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String endereco = root.path("endereco").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("floricultura: tag <pedido_flor> sem endereço p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // ESCAPADA: entrega AGENDADA pra OUTRA pessoa, com cartão. data_entrega (>= hoje, fuso
        // America/Sao_Paulo), periodo ∈ FloriculturaPeriod, destinatario obrigatório, cartao opcional.
        String rawData = root.path("data_entrega").asText(null);
        java.time.LocalDate deliveryDate;
        try {
            deliveryDate = java.time.LocalDate.parse(rawData);
        } catch (Exception e) {
            log.warn("floricultura: data_entrega inválida '{}' na tag <pedido_flor> p/ conversa {} — pedido não criado",
                rawData, conversationId);
            return Optional.empty();
        }
        java.time.LocalDate hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        if (deliveryDate.isBefore(hoje)) {
            log.warn("floricultura: data_entrega no passado ({}) na tag <pedido_flor> p/ conversa {} — pedido não criado",
                deliveryDate, conversationId);
            return Optional.empty();
        }
        String periodo = root.path("periodo").asText(null);
        if (com.meada.profiles.floricultura.FloriculturaPeriod.fromId(periodo).isEmpty()) {
            log.warn("floricultura: periodo inválido '{}' na tag <pedido_flor> p/ conversa {} — pedido não criado",
                periodo, conversationId);
            return Optional.empty();
        }
        String destinatario = root.path("destinatario").asText(null);
        if (destinatario == null || destinatario.isBlank()) {
            log.warn("floricultura: tag <pedido_flor> sem destinatario p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }
        String cartao = root.path("cartao").asText(null);   // opcional (pode ser entrega sem cartão)

        // Onda 1: cupom (#7) — validação/recálculo é do backend — e presente surpresa (#13).
        String cupom = root.path("cupom").asText(null);
        boolean anonimo = root.path("anonimo").asBoolean(false);

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("floricultura: tag <pedido_flor> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item: existe no cardápio do tenant E está disponível. As opções NÃO são
        // validadas aqui (passam adiante; o repo recusa opção fantasma na criação).
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("item_id").asText(null);
            int qtd = itemNode.path("qtd").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("floricultura: item inválido (id/qtd) na tag <pedido_flor> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("floricultura: item_id não-UUID '{}' na tag <pedido_flor> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<FloriculturaCatalogItem> catalogItem = catalogRepository.findById(companyId, itemId);
            if (catalogItem.isEmpty() || !catalogItem.get().available()) {
                log.warn("floricultura: item {} inexistente/indisponível na tag <pedido_flor> p/ conversa {} — pedido não criado",
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
                        log.warn("floricultura: option_id não-UUID '{}' na tag <pedido_flor> p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }
            lines.add(new OrderLineInput(itemId, qtd, optionIds));
        }

        try {
            FloriculturaOrder order = orderService.create(companyId, conversationId, contactId,
                endereco.strip(), lines, null,
                deliveryDate, periodo, destinatario.strip(),
                cartao == null || cartao.isBlank() ? null : cartao.strip(),
                cupom == null || cupom.isBlank() ? null : cupom.strip(), anonimo);
            log.info("floricultura: pedido {} criado p/ conversa {} ({} itens, total {} cents)",
                order.id(), conversationId, lines.size(), order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException (opção fantasma) e IllegalArgumentException (sem linha válida).
            log.warn("floricultura: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
