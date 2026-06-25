package com.meada.whatsapp.profiles.casamento.proposals;

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
 * Extrai a tag {@code <aprovacao_casamento>{...}</aprovacao_casamento>} da resposta da IA e MUTA o
 * estado da proposta (camada 8.7) — o gate de aprovação em 2 fases (clone exato do
 * AprovacaoPropostaHandler do Eventos): a IA altera o estado de um artefato existente, não só cria.
 *
 * <p>{@code decisao} 'aprovada' ou 'recusada'. SÓ é aplicada se a proposta estiver em 'orcada' (senão
 * ignora em silêncio + log — não cria nem altera nada). A transição passa pelo
 * {@link WeddingProposalService#updateStatus} (que valida orcada→aprovada/recusada e dispara a
 * notificação de aprovada). O {@code proposal_id} é resolvido e validado por company.
 */
@Component
public class AprovacaoCasamentoHandler {

    private static final Logger log = LoggerFactory.getLogger(AprovacaoCasamentoHandler.class);

    private static final Pattern TAG = Pattern.compile("<aprovacao_casamento>\\s*(\\{.*?\\})\\s*</aprovacao_casamento>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final WeddingProposalService proposalService;

    public AprovacaoCasamentoHandler(ObjectMapper objectMapper, WeddingProposalService proposalService) {
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
     * id/decisão faltando ou inválida, proposta inexistente, ou a proposta NÃO está em 'orcada'
     * (ignorado). Devolve a proposta atualizada em caso de sucesso.
     */
    public Optional<WeddingProposal> parseAndApply(UUID companyId, UUID conversationId, String aiResponseText) {
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
            log.warn("casamento: tag <aprovacao_casamento> com JSON inválido p/ conversa {} ({}) — ignorada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("proposal_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || decisao == null
                || (!"aprovada".equals(decisao) && !"recusada".equals(decisao))) {
            log.warn("casamento: tag <aprovacao_casamento> com id/decisao faltando ou inválida p/ conversa {} — ignorada",
                conversationId);
            return Optional.empty();
        }

        UUID proposalId;
        try {
            proposalId = UUID.fromString(rawId);
        } catch (RuntimeException e) {
            log.warn("casamento: <aprovacao_casamento> com proposal_id inválido p/ conversa {} — ignorada", conversationId);
            return Optional.empty();
        }

        Optional<WeddingProposal> current = proposalService.get(companyId, proposalId);
        if (current.isEmpty()) {
            log.warn("casamento: <aprovacao_casamento> referencia proposta inexistente {} p/ conversa {} — ignorada",
                proposalId, conversationId);
            return Optional.empty();
        }
        // SÓ muta uma proposta que está aguardando aprovação (orcada). Caso contrário ignora sem efeito.
        if (!"orcada".equals(current.get().status())) {
            log.warn("casamento: <aprovacao_casamento> em proposta {} que NÃO está orcada (status {}) p/ conversa {} — ignorada",
                proposalId, current.get().status(), conversationId);
            return Optional.empty();
        }

        try {
            WeddingProposal updated = proposalService.updateStatus(companyId, proposalId, decisao);
            log.info("casamento: proposta {} → {} via aprovação do cliente p/ conversa {}", proposalId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("casamento: falha ao aplicar <aprovacao_casamento> em proposta {} p/ conversa {} ({}) — ignorada",
                proposalId, conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
