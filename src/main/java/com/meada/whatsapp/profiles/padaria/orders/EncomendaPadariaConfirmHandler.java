package com.meada.whatsapp.profiles.padaria.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.whatsapp.profiles.padaria.PadariaFulfillment;
import com.meada.whatsapp.profiles.padaria.PadariaPeriod;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuItem;
import com.meada.whatsapp.profiles.padaria.menu.PadariaMenuItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <encomenda_padaria>{...}</encomenda_padaria>} da resposta da IA, valida os itens
 * contra o cardápio do tenant e cria o pedido (camada 8.8 / perfil padaria). Clone de
 * {@link com.meada.whatsapp.profiles.floricultura.orders.PedidoFlorConfirmHandler} (mesmos nomes de
 * método: {@code hasOrderTag}/{@code stripOrderTag}/{@code parseAndCreate}) adaptado às escapadas da
 * padaria: fulfillment (retirada/entrega), data CONDICIONAL (pickup_or_delivery_date só obrigatória se
 * há item sob encomenda — validação fina no repositório), personalização de bolo (cake_message por
 * item).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini (constraint: a API trata os dois como mutuamente
 * exclusivos, e o fluxo de outbound já usa responseSchema). A IA emite a tag em texto livre; aqui
 * parseamos via regex.
 *
 * <p>O {@code total} eventual do JSON é DESCARTADO — o repositório recalcula (base + Σ deltas das
 * opções). As OPÇÕES NÃO são validadas aqui; e a regra de lead time / endereço também é do repo — se
 * ele lançar ({@code InvalidOptionException}/{@code LeadTimeViolationException}/
 * {@code AddressRequiredException}), o catch devolve {@link Optional#empty()} (a mensagem da IA segue
 * normal, sem pedido).
 */
@Component
public class EncomendaPadariaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(EncomendaPadariaConfirmHandler.class);

    // <encomenda_padaria> ... </encomenda_padaria> — DOTALL para o JSON poder ter quebras de linha.
    private static final Pattern TAG = Pattern.compile(
        "<encomenda_padaria>\\s*(\\{.*?\\})\\s*</encomenda_padaria>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PadariaMenuItemRepository menuRepository;
    private final PadariaOrderService orderService;

    public EncomendaPadariaConfirmHandler(ObjectMapper objectMapper, PadariaMenuItemRepository menuRepository,
                                          PadariaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.menuRepository = menuRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <encomenda_padaria>...</encomenda_padaria>} do texto (para não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria o pedido. {@link Optional#empty()} quando: não há tag,
     * JSON inválido, fulfillment inválido, período inválido, data no passado, nenhum item válido
     * (menu_item inexistente ou indisponível), ou o repo recusa por opção inválida / lead time /
     * endereço ausente em 'entrega'.
     */
    public Optional<PadariaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("padaria: tag <encomenda_padaria> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // fulfillment: retirada | entrega.
        String fulfillment = root.path("fulfillment").asText(null);
        if (PadariaFulfillment.fromId(fulfillment).isEmpty()) {
            log.warn("padaria: fulfillment inválido '{}' na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                fulfillment, conversationId);
            return Optional.empty();
        }

        // delivery_address: nullable (validado no repo conforme o fulfillment).
        String enderecoRaw = root.path("delivery_address").asText(null);
        String endereco = enderecoRaw == null || enderecoRaw.isBlank() ? null : enderecoRaw.strip();

        // pickup_or_delivery_date: CONDICIONAL (nullable). Se vier, parseia e rejeita passado; a
        // obrigatoriedade (item sob encomenda) é checada no repo (que também conhece o lead).
        LocalDate pickupOrDeliveryDate = null;
        JsonNode dateNode = root.path("pickup_or_delivery_date");
        if (!dateNode.isMissingNode() && !dateNode.isNull()) {
            String rawData = dateNode.asText(null);
            if (rawData != null && !rawData.isBlank()) {
                try {
                    pickupOrDeliveryDate = LocalDate.parse(rawData);
                } catch (Exception e) {
                    log.warn("padaria: pickup_or_delivery_date inválida '{}' na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                        rawData, conversationId);
                    return Optional.empty();
                }
                LocalDate hoje = LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
                if (pickupOrDeliveryDate.isBefore(hoje)) {
                    log.warn("padaria: pickup_or_delivery_date no passado ({}) na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                        pickupOrDeliveryDate, conversationId);
                    return Optional.empty();
                }
            }
        }

        // delivery_period: nullable; se vier, deve estar em PadariaPeriod.
        String periodo = null;
        JsonNode periodNode = root.path("delivery_period");
        if (!periodNode.isMissingNode() && !periodNode.isNull()) {
            String rawPeriod = periodNode.asText(null);
            if (rawPeriod != null && !rawPeriod.isBlank()) {
                if (PadariaPeriod.fromId(rawPeriod).isEmpty()) {
                    log.warn("padaria: delivery_period inválido '{}' na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                        rawPeriod, conversationId);
                    return Optional.empty();
                }
                periodo = rawPeriod;
            }
        }

        String notesRaw = root.path("notes").asText(null);
        String notes = notesRaw == null || notesRaw.isBlank() ? null : notesRaw.strip();

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("padaria: tag <encomenda_padaria> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // Valida cada item: existe no cardápio do tenant E está disponível. As opções NÃO são
        // validadas aqui (passam adiante; o repo recusa opção fantasma na criação).
        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("menu_item_id").asText(null);
            int qtd = itemNode.path("quantity").asInt(0);
            if (rawId == null || qtd <= 0) {
                log.warn("padaria: item inválido (id/quantity) na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                    conversationId);
                return Optional.empty();
            }
            UUID itemId;
            try {
                itemId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("padaria: menu_item_id não-UUID '{}' na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<PadariaMenuItem> menuItem = menuRepository.findById(companyId, itemId);
            if (menuItem.isEmpty() || !menuItem.get().available()) {
                log.warn("padaria: item {} inexistente/indisponível na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                    itemId, conversationId);
                return Optional.empty();
            }

            // Parse das opções (array de objetos {option_id}; opcional — item sem opção → lista vazia).
            List<UUID> optionIds = new ArrayList<>();
            JsonNode optionsNode = itemNode.path("options");
            if (optionsNode.isArray()) {
                for (JsonNode optNode : optionsNode) {
                    String rawOpt = optNode.path("option_id").asText(null);
                    if (rawOpt == null || rawOpt.isBlank()) {
                        continue;
                    }
                    try {
                        optionIds.add(UUID.fromString(rawOpt));
                    } catch (IllegalArgumentException e) {
                        log.warn("padaria: option_id não-UUID '{}' na tag <encomenda_padaria> p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }

            // cake_message (ESCAPADA 2): texto livre da placa, opcional (nullable).
            String cakeMessageRaw = itemNode.path("cake_message").asText(null);
            String cakeMessage = cakeMessageRaw == null || cakeMessageRaw.isBlank() ? null : cakeMessageRaw.strip();

            lines.add(new OrderLineInput(itemId, qtd, optionIds, cakeMessage));
        }

        try {
            PadariaOrder order = orderService.create(companyId, conversationId, contactId,
                fulfillment, endereco, lines, pickupOrDeliveryDate, periodo, notes);
            log.info("padaria: pedido {} criado p/ conversa {} ({} itens, fulfillment {}, total {} cents)",
                order.id(), conversationId, lines.size(), fulfillment, order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOptionException, LeadTimeViolationException, AddressRequiredException e
            // IllegalArgumentException (sem linha válida).
            log.warn("padaria: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
