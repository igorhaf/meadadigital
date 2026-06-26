package com.meada.whatsapp.profiles.viagens.proposals;

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
 * Extrai a tag {@code <aprovacao_viagem>{...}</aprovacao_viagem>} da resposta da IA e MUTA o estado da
 * proposta (camada 8.18 / perfil viagens) — o gate de aprovação em 2 fases (clone EXATO do
 * AprovacaoPropostaHandler, chassi eventos 8.2): a IA altera o estado de um artefato existente, não só
 * cria.
 *
 * <p>{@code decisao} 'aprovada' ou 'recusada'. SÓ é aplicada se a proposta estiver em 'orcada' (senão
 * ignora em silêncio + log — não cria nem altera nada). A transição passa pelo
 * {@link TravelProposalService#updateStatus} (que valida orcada→aprovada/recusada e dispara a
 * notificação de aprovada). O {@code proposal_id} é resolvido e validado por company.
 */
@Component
public class AprovacaoViagemHandler {

    private static final Logger log = LoggerFactory.getLogger(AprovacaoViagemHandler.class);

    private static final Pattern TAG = Pattern.compile("<aprovacao_viagem>\\s*(\\{.*?\\})\\s*</aprovacao_viagem>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final TravelProposalService proposalService;

    public AprovacaoViagemHandler(ObjectMapper objectMapper, TravelProposalService proposalService) {
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
    public Optional<TravelProposal> parseAndApply(UUID companyId, UUID conversationId, String aiResponseText) {
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
            log.warn("viagens: tag <aprovacao_viagem> com JSON inválido p/ conversa {} ({}) — ignorada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("proposal_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || decisao == null
                || (!"aprovada".equals(decisao) && !"recusada".equals(decisao))) {
            log.warn("viagens: tag <aprovacao_viagem> com id/decisao faltando ou inválida p/ conversa {} — ignorada",
                conversationId);
            return Optional.empty();
        }

        UUID proposalId;
        try {
            proposalId = UUID.fromString(rawId);
        } catch (RuntimeException e) {
            log.warn("viagens: <aprovacao_viagem> com proposal_id inválido p/ conversa {} — ignorada", conversationId);
            return Optional.empty();
        }

        Optional<TravelProposal> current = proposalService.get(companyId, proposalId);
        if (current.isEmpty()) {
            log.warn("viagens: <aprovacao_viagem> referencia proposta inexistente {} p/ conversa {} — ignorada",
                proposalId, conversationId);
            return Optional.empty();
        }
        // SÓ muta uma proposta que está aguardando aprovação (orcada). Caso contrário ignora sem efeito.
        if (!"orcada".equals(current.get().status())) {
            log.warn("viagens: <aprovacao_viagem> em proposta {} que NÃO está orcada (status {}) p/ conversa {} — ignorada",
                proposalId, current.get().status(), conversationId);
            return Optional.empty();
        }

        try {
            TravelProposal updated = proposalService.updateStatus(companyId, proposalId, decisao);
            log.info("viagens: proposta {} → {} via aprovação do cliente p/ conversa {}", proposalId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("viagens: falha ao aplicar <aprovacao_viagem> em proposta {} p/ conversa {} ({}) — ignorada",
                proposalId, conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
