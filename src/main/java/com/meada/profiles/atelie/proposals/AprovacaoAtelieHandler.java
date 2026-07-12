package com.meada.profiles.atelie.proposals;

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
 * Extrai a tag {@code <aprovacao_atelie>{...}</aprovacao_atelie>} da resposta da IA e MUTA o estado
 * da proposta (camada 8.14) — o gate de aprovação em 2 fases (clone exato do AprovacaoPropostaHandler
 * do Eventos): a IA altera o estado de um artefato existente, não só cria.
 *
 * <p>{@code decisao} 'aprovada' ou 'recusada'. SÓ é aplicada se a proposta estiver em 'orcada' (senão
 * ignora em silêncio + log — não cria nem altera nada). A transição passa pelo
 * {@link AtelieProposalService#updateStatus} (que valida orcada→aprovada/recusada e dispara a
 * notificação de aprovada). O {@code proposal_id} é resolvido e validado por company.
 */
@Component
public class AprovacaoAtelieHandler {

    private static final Logger log = LoggerFactory.getLogger(AprovacaoAtelieHandler.class);

    private static final Pattern TAG = Pattern.compile("<aprovacao_atelie>\\s*(\\{.*?\\})\\s*</aprovacao_atelie>",
        Pattern.DOTALL);

    private final ObjectMapper objectMapper;
    private final AtelieProposalService proposalService;

    public AprovacaoAtelieHandler(ObjectMapper objectMapper, AtelieProposalService proposalService) {
        this.objectMapper = objectMapper;
        this.proposalService = proposalService;
    }

    public boolean hasOrderTag(String text) {
        return text != null && TAG.matcher(text).find();
    }

    public String stripOrderTag(String text) {
        if (text == null) {
            return null;
        }
        return TAG.matcher(text).replaceAll("").stripTrailing();
    }

    /**
     * Extrai a tag e aplica a decisão. {@link Optional#empty()} quando: não há tag, JSON inválido,
     * id/decisão faltando ou inválida, proposta inexistente, ou a proposta NÃO está em 'orcada'
     * (ignorado). Devolve a proposta atualizada em caso de sucesso. {@code contactId} aceito por
     * paridade de assinatura com o handler de abertura (não usado — a proposta é resolvida por id).
     */
    public Optional<AtelieProposal> parseAndApply(UUID companyId, UUID conversationId, UUID contactId,
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
            log.warn("atelie: tag <aprovacao_atelie> com JSON inválido p/ conversa {} ({}) — ignorada",
                conversationId, e.getMessage());
            return Optional.empty();
        }

        String rawId = root.path("proposal_id").asText(null);
        String decisao = root.path("decisao").asText(null);
        if (rawId == null || decisao == null
                || (!"aprovada".equals(decisao) && !"recusada".equals(decisao))) {
            log.warn("atelie: tag <aprovacao_atelie> com id/decisao faltando ou inválida p/ conversa {} — ignorada",
                conversationId);
            return Optional.empty();
        }

        UUID proposalId;
        try {
            proposalId = UUID.fromString(rawId);
        } catch (RuntimeException e) {
            log.warn("atelie: <aprovacao_atelie> com proposal_id inválido p/ conversa {} — ignorada", conversationId);
            return Optional.empty();
        }

        Optional<AtelieProposal> current = proposalService.get(companyId, proposalId);
        if (current.isEmpty()) {
            log.warn("atelie: <aprovacao_atelie> referencia proposta inexistente {} p/ conversa {} — ignorada",
                proposalId, conversationId);
            return Optional.empty();
        }
        // BARREIRA DE CONTATO: a aprovação só vale vinda do contato DONO da proposta — impede que a
        // tag (id alucinado/chutado) aprove/recuse a proposta de outro cliente do mesmo tenant.
        if (contactId == null || !java.util.Objects.equals(current.get().contactId(), contactId)) {
            log.warn("atelie: <aprovacao_atelie> em proposta de outro contato (proposta {} contato {} ≠ conversa {}) — bloqueada",
                proposalId, current.get().contactId(), contactId);
            return Optional.empty();
        }
        // SÓ muta uma proposta que está aguardando aprovação (orcada). Caso contrário ignora sem efeito.
        if (!"orcada".equals(current.get().status())) {
            log.warn("atelie: <aprovacao_atelie> em proposta {} que NÃO está orcada (status {}) p/ conversa {} — ignorada",
                proposalId, current.get().status(), conversationId);
            return Optional.empty();
        }

        try {
            AtelieProposal updated = proposalService.updateStatus(companyId, proposalId, decisao);
            log.info("atelie: proposta {} → {} via aprovação do cliente p/ conversa {}", proposalId, decisao, conversationId);
            return Optional.of(updated);
        } catch (RuntimeException e) {
            log.warn("atelie: falha ao aplicar <aprovacao_atelie> em proposta {} p/ conversa {} ({}) — ignorada",
                proposalId, conversationId, e.getMessage());
            return Optional.empty();
        }
    }
}
