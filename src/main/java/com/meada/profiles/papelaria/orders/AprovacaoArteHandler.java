package com.meada.profiles.papelaria.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meada.messaging.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <aprovacao_arte>{...}</aprovacao_arte>} da resposta da IA e APROVA a arte de um
 * pedido EXISTENTE (camada 8.15 / perfil papelaria) — a ESCAPADA: a IA muta o estado de um artefato
 * existente, não só cria. Espelho do
 * {@link com.meada.profiles.oficina.orders.AprovacaoOsHandler} (camada 7.9; aprovação da OS),
 * mas aqui a aprovação é da ARTE: seta {@code art_approved=true} e move arte_aprovacao→em_producao.
 *
 * <p>{@code order_id} é OPCIONAL: se vier, resolve o pedido por id (e por company); se for null/ausente,
 * resolve o pedido em 'arte_aprovacao' da conversa do contato. A aplicação SÓ ocorre se o pedido estiver
 * em 'arte_aprovacao' (senão ignora em silêncio + log — best-effort, não cria nem altera nada). A IA
 * NÃO sobe arte nem aceita/recusa pedido (ações humanas do painel).
 */
@Component
public class AprovacaoArteHandler {

    private static final Logger log = LoggerFactory.getLogger(AprovacaoArteHandler.class);

    private static final Pattern TAG = Pattern.compile("<aprovacao_arte>\\s*(\\{.*?\\})\\s*</aprovacao_arte>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final PapelariaOrderService orderService;
    private final ConversationRepository conversationRepository;

    public AprovacaoArteHandler(ObjectMapper objectMapper, PapelariaOrderService orderService,
                                ConversationRepository conversationRepository) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.conversationRepository = conversationRepository;
    }

    public boolean hasTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e aplica a aprovação da arte. {@link Optional#empty()} quando: não há tag, JSON
     * inválido, order_id inválido, nenhum pedido resolvido, ou o pedido NÃO está em 'arte_aprovacao'
     * (ignorado). Devolve o pedido atualizado (já em em_producao) em caso de sucesso.
     */
    public Optional<PapelariaOrder> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
                                                  String aiResponseText) {
        if (aiResponseText == null) {
            return Optional.empty();
        }
        Matcher m = TAG.matcher(aiResponseText);
        if (!m.find()) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(m.group(1));
        } catch (Exception e) {
            log.warn("papelaria: tag <aprovacao_arte> com JSON inválido p/ conversa {} ({}) — ignorada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        // order_id é OPCIONAL: se vier, usa-o; senão resolve pelo pedido em 'arte_aprovacao' da conversa.
        Optional<PapelariaOrder> current;
        JsonNode idNode = root.path("order_id");
        String rawId = idNode.isMissingNode() || idNode.isNull() ? null : idNode.asText(null);
        if (rawId != null && !rawId.isBlank()) {
            UUID orderId;
            try {
                orderId = UUID.fromString(rawId);
            } catch (RuntimeException e) {
                log.warn("papelaria: <aprovacao_arte> com order_id inválido p/ conversa {} — ignorada", conversationId);
                return Optional.empty();
            }
            current = orderService.get(companyId, orderId);
        } else {
            current = orderService.getArteAprovacaoByConversation(companyId, conversationId);
        }

        if (current.isEmpty()) {
            log.warn("papelaria: <aprovacao_arte> não resolveu pedido p/ conversa {} — ignorada", conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: a aprovação só vale vinda do contato DONO do pedido — no modo
        // order_id explícito, um id alucinado/chutado não pode aprovar a arte de outro cliente.
        // O pedido não guarda contact_id; o dono é o contato da conversa de origem do pedido.
        UUID ownerContact = current.get().conversationId() == null ? null
            : conversationRepository.findContactIdByConversation(current.get().conversationId()).orElse(null);
        if (contactId == null || ownerContact == null || !ownerContact.equals(contactId)) {
            log.warn("papelaria: <aprovacao_arte> em pedido de outro contato (pedido {} dono {} ≠ conversa {}) — bloqueada",
                current.get().id(), ownerContact, contactId);
            return Optional.empty();
        }
        // SÓ aprova um pedido que está aguardando aprovação da arte (arte_aprovacao). Senão ignora.
        if (!"arte_aprovacao".equals(current.get().status())) {
            log.warn("papelaria: <aprovacao_arte> em pedido {} que NÃO está em arte_aprovacao (status {}) p/ conversa {} — ignorada",
                current.get().id(), current.get().status(), conversationId);
            return Optional.empty();
        }

        try {
            PapelariaOrder updated = orderService.approveArt(companyId, current.get().id());
            log.info("papelaria: pedido {} → em_producao via aprovação da arte do cliente p/ conversa {}",
                updated.id(), conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("papelaria: falha ao aplicar <aprovacao_arte> em pedido {} p/ conversa {} ({}) — ignorada",
                current.get().id(), conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
