package com.meada.profiles.oficina.orders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a tag {@code <aprovacao_os>{...}</aprovacao_os>} da resposta da IA e MUTA o estado da OS
 * (camada 7.9) — a NOVIDADE da SM: a IA altera o estado de um artefato existente, não só cria.
 *
 * <p>{@code decisao} 'aprovada' ou 'recusada'. SÓ é aplicada se a OS estiver em 'orcada' (senão
 * ignora em silêncio + log — não cria nem altera nada). A transição passa pelo
 * {@link ServiceOrderService#updateStatus} (que valida orcada→aprovada/recusada e dispara a
 * notificação de aprovada). O {@code service_order_id} é resolvido e validado por company.
 */
@Component
public class AprovacaoOsHandler {

    private static final Logger log = LoggerFactory.getLogger(AprovacaoOsHandler.class);

    private static final Pattern TAG = Pattern.compile("<aprovacao_os>\\s*(\\{.*?\\})\\s*</aprovacao_os>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final ServiceOrderService orderService;

    public AprovacaoOsHandler(ObjectMapper objectMapper, ServiceOrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
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
     * Extrai a tag e aplica a decisão. {@link Optional#empty()} quando: não há tag, JSON inválido,
     * id/decisão faltando ou inválida, OS inexistente, OS de OUTRO contato (barreira de contato),
     * ou a OS NÃO está em 'orcada' (ignorado). Devolve a OS atualizada em caso de sucesso.
     */
    public Optional<ServiceOrder> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("oficina: tag <aprovacao_os> com JSON inválido p/ conversa {} ({}) — ignorada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("service_order_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || decisao == null
                || (!"aprovada".equals(decisao) && !"recusada".equals(decisao))) {
            log.warn("oficina: tag <aprovacao_os> com id/decisao faltando ou inválida p/ conversa {} — ignorada",
                conversationId);
            return Optional.empty();
        }

        UUID orderId;
        try {
            orderId = UUID.fromString(rawId);
        } catch (RuntimeException e) {
            log.warn("oficina: <aprovacao_os> com service_order_id inválido p/ conversa {} — ignorada", conversationId);
            return Optional.empty();
        }

        Optional<ServiceOrder> current = orderService.get(companyId, orderId);
        if (current.isEmpty()) {
            log.warn("oficina: <aprovacao_os> referencia OS inexistente {} p/ conversa {} — ignorada", orderId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: a aprovação só vale vinda do contato DONO da OS — impede que a tag
        // (id alucinado/chutado) aprove/recuse a OS de outro cliente do mesmo tenant.
        if (contactId == null || !java.util.Objects.equals(current.get().contactId(), contactId)) {
            log.warn("oficina: <aprovacao_os> em OS de outro contato (OS {} contato {} ≠ conversa {}) — bloqueada",
                orderId, current.get().contactId(), contactId);
            return Optional.empty();
        }
        // SÓ muta uma OS que está aguardando aprovação (orcada). Caso contrário ignora sem efeito.
        if (!"orcada".equals(current.get().status())) {
            log.warn("oficina: <aprovacao_os> em OS {} que NÃO está orcada (status {}) p/ conversa {} — ignorada",
                orderId, current.get().status(), conversationId);
            return Optional.empty();
        }

        try {
            ServiceOrder updated = orderService.updateStatus(companyId, orderId, decisao);
            log.info("oficina: OS {} → {} via aprovação do cliente p/ conversa {}", orderId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("oficina: falha ao aplicar <aprovacao_os> em OS {} p/ conversa {} ({}) — ignorada",
                orderId, conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
