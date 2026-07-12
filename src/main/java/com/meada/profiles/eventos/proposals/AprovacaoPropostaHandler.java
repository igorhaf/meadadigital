package com.meada.profiles.eventos.proposals;

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
 * Extrai a tag {@code <aprovacao_proposta>{...}</aprovacao_proposta>} da resposta da IA e MUTA o
 * estado da proposta (camada 8.2) — o gate de aprovação em 2 fases (clone exato do AprovacaoOsHandler
 * do Oficina): a IA altera o estado de um artefato existente, não só cria.
 *
 * <p>{@code decisao} 'aprovada' ou 'recusada'. SÓ é aplicada se a proposta estiver em 'orcada' (senão
 * ignora em silêncio + log — não cria nem altera nada). A transição passa pelo
 * {@link EventProposalService#updateStatus} (que valida orcada→aprovada/recusada e dispara a
 * notificação de aprovada). O {@code proposal_id} é resolvido e validado por company.
 */
@Component
public class AprovacaoPropostaHandler {

    private static final Logger log = LoggerFactory.getLogger(AprovacaoPropostaHandler.class);

    private static final Pattern TAG = Pattern.compile("<aprovacao_proposta>\\s*(\\{.*?\\})\\s*</aprovacao_proposta>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final EventProposalService proposalService;

    public AprovacaoPropostaHandler(ObjectMapper objectMapper, EventProposalService proposalService) {
        this.objectMapper = objectMapper;
        this.proposalService = proposalService;
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
     * id/decisão faltando ou inválida, proposta inexistente, proposta de OUTRO contato (barreira
     * de contato), ou a proposta NÃO está em 'orcada' (ignorado). Devolve a proposta atualizada
     * em caso de sucesso.
     */
    public Optional<EventProposal> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("eventos: tag <aprovacao_proposta> com JSON inválido p/ conversa {} ({}) — ignorada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("proposal_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || decisao == null
                || (!"aprovada".equals(decisao) && !"recusada".equals(decisao))) {
            log.warn("eventos: tag <aprovacao_proposta> com id/decisao faltando ou inválida p/ conversa {} — ignorada",
                conversationId);
            return Optional.empty();
        }

        UUID proposalId;
        try {
            proposalId = UUID.fromString(rawId);
        } catch (RuntimeException e) {
            log.warn("eventos: <aprovacao_proposta> com proposal_id inválido p/ conversa {} — ignorada", conversationId);
            return Optional.empty();
        }

        Optional<EventProposal> current = proposalService.get(companyId, proposalId);
        if (current.isEmpty()) {
            log.warn("eventos: <aprovacao_proposta> referencia proposta inexistente {} p/ conversa {} — ignorada",
                proposalId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: a aprovação só vale vinda do contato DONO da proposta — impede que a
        // tag (id alucinado/chutado) aprove/recuse a proposta de outro cliente do mesmo tenant.
        if (contactId == null || !java.util.Objects.equals(current.get().contactId(), contactId)) {
            log.warn("eventos: <aprovacao_proposta> em proposta de outro contato (proposta {} contato {} ≠ conversa {}) — bloqueada",
                proposalId, current.get().contactId(), contactId);
            return Optional.empty();
        }
        // SÓ muta uma proposta que está aguardando aprovação (orcada). Caso contrário ignora sem efeito.
        if (!"orcada".equals(current.get().status())) {
            log.warn("eventos: <aprovacao_proposta> em proposta {} que NÃO está orcada (status {}) p/ conversa {} — ignorada",
                proposalId, current.get().status(), conversationId);
            return Optional.empty();
        }

        try {
            EventProposal updated = proposalService.updateStatus(companyId, proposalId, decisao);
            log.info("eventos: proposta {} → {} via aprovação do cliente p/ conversa {}", proposalId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("eventos: falha ao aplicar <aprovacao_proposta> em proposta {} p/ conversa {} ({}) — ignorada",
                proposalId, conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
