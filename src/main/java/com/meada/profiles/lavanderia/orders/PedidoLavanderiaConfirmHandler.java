package com.meada.profiles.lavanderia.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.profiles.lavanderia.LavanderiaPeriod;
import com.meada.profiles.lavanderia.services.LavanderiaService;
import com.meada.profiles.lavanderia.services.LavanderiaServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <pedido_lavanderia>{...}</pedido_lavanderia>} da resposta da IA, valida os itens
 * contra o catálogo do tenant e cria o pedido (camada 8.10). Clone do PedidoFlorConfirmHandler + a
 * ESCAPADA das DUAS DATAS (collect_date >= hoje + delivery_date opcional validada por turnaround no
 * service).
 *
 * <p>NÃO usa tool calling / responseSchema do Gemini. A IA emite a tag em texto livre; aqui parseamos
 * via regex. O {@code total_cents} (se vier) é DESCARTADO — o repositório recalcula. As OPÇÕES não são
 * validadas aqui; se o repo lançar {@code InvalidOptionException} (opção fantasma), o catch devolve
 * {@link Optional#empty()}. As validações de negócio (collect_date no passado, turnaround_violation,
 * below_minimum, address_required) lançam exceções no service que aqui viram {@link Optional#empty()}.
 *
 * <p>Tag: {@code <pedido_lavanderia>{"collect_date":"YYYY-MM-DD","period":"manha|tarde",
 * "delivery_address":"...","delivery_date":"YYYY-MM-DD"|null,"items":[{"service_id":"UUID",
 * "options":["UUID"],"qty":N}],"notes":"..."}</pedido_lavanderia>}.
 */
@Component
public class PedidoLavanderiaConfirmHandler {

    private static final Logger log = LoggerFactory.getLogger(PedidoLavanderiaConfirmHandler.class);

    private static final Pattern TAG = Pattern.compile(
        "<pedido_lavanderia>\\s*(\\{.*?\\})\\s*</pedido_lavanderia>", Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final LavanderiaServiceRepository serviceRepository;
    private final LavanderiaOrderService orderService;

    public PedidoLavanderiaConfirmHandler(ObjectMapper objectMapper,
                                          LavanderiaServiceRepository serviceRepository,
                                          LavanderiaOrderService orderService) {
        this.objectMapper = objectMapper;
        this.serviceRepository = serviceRepository;
        this.orderService = orderService;
    }

    /** True se o texto contém a tag de pedido (decisão rápida sem parsear). */
    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    /** Remove a tag {@code <pedido_lavanderia>...</pedido_lavanderia>} do texto (para não enviá-la ao cliente). */
    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag, valida os itens e cria o pedido. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, dados de coleta/entrega inválidos, nenhum item válido, ou o service recusa (opção
     * inválida / turnaround / mínimo / endereço / collect_date no passado).
     */
    public Optional<LavanderiaOrder> parseAndCreate(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("lavanderia: tag <pedido_lavanderia> com JSON inválido p/ conversa {} ({}) — pedido não criado",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String endereco = root.path("delivery_address").asText(null);
        if (endereco == null || endereco.isBlank()) {
            log.warn("lavanderia: tag <pedido_lavanderia> sem delivery_address p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        // ESCAPADA: collect_date (>= hoje, validado no service), delivery_date OPCIONAL (null → materializa).
        String rawCollect = root.path("collect_date").asText(null);
        LocalDate collectDate;
        try {
            collectDate = LocalDate.parse(rawCollect);
        } catch (Exception e) {
            log.warn("lavanderia: collect_date inválida '{}' na tag p/ conversa {} — pedido não criado",
                rawCollect, conversationId);
            return Optional.empty();
        }
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        if (collectDate.isBefore(hoje)) {
            log.warn("lavanderia: collect_date no passado ({}) na tag p/ conversa {} — pedido não criado",
                collectDate, conversationId);
            return Optional.empty();
        }

        // delivery_date opcional: ausente ou "null"/vazio → null (o service materializa).
        LocalDate requestedDeliveryDate = null;
        JsonNode ddNode = root.path("delivery_date");
        if (!ddNode.isMissingNode() && !ddNode.isNull()) {
            String rawDelivery = ddNode.asText(null);
            if (rawDelivery != null && !rawDelivery.isBlank() && !"null".equalsIgnoreCase(rawDelivery)) {
                try {
                    requestedDeliveryDate = LocalDate.parse(rawDelivery);
                } catch (Exception e) {
                    log.warn("lavanderia: delivery_date inválida '{}' na tag p/ conversa {} — pedido não criado",
                        rawDelivery, conversationId);
                    return Optional.empty();
                }
            }
        }

        String periodo = root.path("period").asText(null);
        if (LavanderiaPeriod.fromId(periodo).isEmpty()) {
            log.warn("lavanderia: period inválido '{}' na tag p/ conversa {} — pedido não criado",
                periodo, conversationId);
            return Optional.empty();
        }

        String notes = root.path("notes").asText(null);

        // Onda 1: cupom (#6) e express (#2) — validação/recálculo é do backend.
        String cupom = root.path("cupom").asText(null);
        boolean express = root.path("express").asBoolean(false);

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            log.warn("lavanderia: tag <pedido_lavanderia> sem items p/ conversa {} — pedido não criado", conversationId);
            return Optional.empty();
        }

        List<OrderLineInput> lines = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            String rawId = itemNode.path("service_id").asText(null);
            int qty = itemNode.path("qty").asInt(0);
            if (rawId == null || qty <= 0) {
                log.warn("lavanderia: item inválido (id/qty) na tag p/ conversa {} — pedido não criado", conversationId);
                return Optional.empty();
            }
            UUID serviceId;
            try {
                serviceId = UUID.fromString(rawId);
            } catch (IllegalArgumentException e) {
                log.warn("lavanderia: service_id não-UUID '{}' na tag p/ conversa {} — pedido não criado",
                    rawId, conversationId);
                return Optional.empty();
            }
            Optional<LavanderiaService> svc = serviceRepository.findById(companyId, serviceId);
            if (svc.isEmpty() || !svc.get().available()) {
                log.warn("lavanderia: serviço {} inexistente/indisponível na tag p/ conversa {} — pedido não criado",
                    serviceId, conversationId);
                return Optional.empty();
            }

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
                        log.warn("lavanderia: option_id não-UUID '{}' na tag p/ conversa {} — pedido não criado",
                            rawOpt, conversationId);
                        return Optional.empty();
                    }
                }
            }
            lines.add(new OrderLineInput(serviceId, qty, optionIds));
        }

        try {
            LavanderiaOrder order = orderService.create(companyId, conversationId, contactId,
                endereco, lines, notes == null || notes.isBlank() ? null : notes.strip(),
                collectDate, requestedDeliveryDate, periodo,
                cupom == null || cupom.isBlank() ? null : cupom.strip(), express);
            log.info("lavanderia: pedido {} criado p/ conversa {} ({} itens, coleta {}, entrega {}, total {} cents)",
                order.id(), conversationId, lines.size(), order.collectDate(), order.deliveryDate(),
                order.totalCents());
            return Optional.of(order);
        } catch (RuntimeException e) {
            // Inclui InvalidOption, TurnaroundViolation, BelowMinimum, AddressRequired, CollectDateInPast.
            log.warn("lavanderia: falha ao criar pedido p/ conversa {} ({}) — mensagem segue sem pedido",
                conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
